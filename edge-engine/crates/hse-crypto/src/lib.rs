//! HSE-1 (Hokeka Secure Envelope v1) — edge/open side.
//!
//! Byte-for-byte compatible with the Java control-plane implementation
//! (`BACKEND/.../edge/crypto/HokekaSecureEnvelope.java`). Verified against the shared test vectors
//! in `edge-engine/test-vectors/`.
//!
//! Wire format (big-endian ints):
//! ```text
//!   off  len  field
//!   0    4    magic  = "HSE1"
//!   4    1    version = 1
//!   5    32   ephemeral X25519 public key (RFC-7748 little-endian)
//!   37   12   ChaCha20-Poly1305 nonce
//!   49   4    ciphertext length N
//!   53   N    ciphertext (AEAD, includes 16-byte tag)
//!   53+N 64   Ed25519 signature over bytes [0 .. 53+N)
//! ```

use chacha20poly1305::aead::{Aead, KeyInit, Payload};
use chacha20poly1305::{ChaCha20Poly1305, Key, Nonce};
use ed25519_dalek::{Signature, Verifier, VerifyingKey};
use hkdf::Hkdf;
use sha2::Sha256;
use x25519_dalek::{PublicKey as XPublicKey, StaticSecret};

pub const MAGIC: &[u8; 4] = b"HSE1";
pub const VERSION: u8 = 1;
const EPK_LEN: usize = 32;
const NONCE_LEN: usize = 12;
const SIG_LEN: usize = 64;
const TAG_LEN: usize = 16;
const HEADER_LEN: usize = 4 + 1 + EPK_LEN + NONCE_LEN + 4; // 53
const HKDF_INFO_PREFIX: &[u8] = b"hokeka-hse1-v1";

#[derive(Debug)]
pub enum HseError {
    TooShort,
    BadMagic,
    BadVersion,
    BadFraming,
    BadSignature,
    Decrypt,
}

impl std::fmt::Display for HseError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{:?}", self)
    }
}
impl std::error::Error for HseError {}

/// Open (edge): verify the control-plane signature, then ECDH → HKDF → AEAD-decrypt the payload.
///
/// * `envelope`         – the sealed bytes
/// * `edge_x25519_priv` – the edge's long-term X25519 secret (raw 32 bytes)
/// * `cp_ed25519_pub`   – the control plane's Ed25519 public key, pinned at the edge (raw 32 bytes)
/// * `context`          – the same binding context the control plane sealed with (e.g. the PSP id)
pub fn open(
    envelope: &[u8],
    edge_x25519_priv: &[u8; 32],
    cp_ed25519_pub: &[u8; 32],
    context: &[u8],
) -> Result<Vec<u8>, HseError> {
    if envelope.len() < HEADER_LEN + TAG_LEN + SIG_LEN {
        return Err(HseError::TooShort);
    }
    if &envelope[0..4] != MAGIC {
        return Err(HseError::BadMagic);
    }
    if envelope[4] != VERSION {
        return Err(HseError::BadVersion);
    }
    let epk: [u8; 32] = envelope[5..37].try_into().unwrap();
    let nonce_bytes: [u8; 12] = envelope[37..49].try_into().unwrap();
    let ct_len = u32::from_be_bytes(envelope[49..53].try_into().unwrap()) as usize;
    if ct_len < TAG_LEN || envelope.len() != HEADER_LEN + ct_len + SIG_LEN {
        return Err(HseError::BadFraming);
    }
    let signed_len = HEADER_LEN + ct_len;
    let ciphertext = &envelope[HEADER_LEN..signed_len];
    let signature = &envelope[signed_len..signed_len + SIG_LEN];

    // 1) Authenticity — signature over everything before it.
    let vk = VerifyingKey::from_bytes(cp_ed25519_pub).map_err(|_| HseError::BadSignature)?;
    let sig = Signature::from_slice(signature).map_err(|_| HseError::BadSignature)?;
    vk.verify(&envelope[0..signed_len], &sig)
        .map_err(|_| HseError::BadSignature)?;

    // 2) Confidentiality — ECDH → HKDF → AEAD.
    let secret = StaticSecret::from(*edge_x25519_priv);
    let epk_pub = XPublicKey::from(epk);
    let shared = secret.diffie_hellman(&epk_pub);

    let mut info = Vec::with_capacity(HKDF_INFO_PREFIX.len() + context.len());
    info.extend_from_slice(HKDF_INFO_PREFIX);
    info.extend_from_slice(context);

    let hk = Hkdf::<Sha256>::new(Some(MAGIC), shared.as_bytes());
    let mut key_bytes = [0u8; 32];
    hk.expand(&info, &mut key_bytes)
        .map_err(|_| HseError::Decrypt)?;

    // AAD = magic|version|epk|nonce (first 49 bytes), same as Java.
    let aad = &envelope[0..HEADER_LEN - 4];

    let cipher = ChaCha20Poly1305::new(Key::from_slice(&key_bytes));
    cipher
        .decrypt(
            Nonce::from_slice(&nonce_bytes),
            Payload {
                msg: ciphertext,
                aad,
            },
        )
        .map_err(|_| HseError::Decrypt)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    struct Vector {
        edge_priv: [u8; 32],
        cp_pub: [u8; 32],
        envelope: Vec<u8>,
        context: Vec<u8>,
        plaintext: Vec<u8>,
    }

    /// The interop vector is committed at `edge-engine/test-vectors/hse1_vector.json` (regenerate
    /// with the Java `HseVectorGeneratorTest`). It runs by default: if either side's wire format
    /// drifts, these tests must fail loudly rather than skip.
    fn vector() -> Vector {
        let raw = fs::read("../../test-vectors/hse1_vector.json").expect(
            "test-vectors/hse1_vector.json missing — regenerate it with the Java HseVectorGeneratorTest",
        );
        let v: serde_json::Value = serde_json::from_slice(&raw).unwrap();
        let hexd = |k: &str| hex_to_bytes(v[k].as_str().unwrap());
        Vector {
            edge_priv: hexd("edge_x25519_priv").try_into().unwrap(),
            cp_pub: hexd("cp_ed25519_pub").try_into().unwrap(),
            envelope: hexd("envelope"),
            context: hexd("context"),
            plaintext: hexd("plaintext"),
        }
    }

    // Proves the Rust edge opens byte-for-byte what the Java control plane sealed.
    #[test]
    fn opens_java_sealed_vector() {
        let v = vector();
        let opened =
            open(&v.envelope, &v.edge_priv, &v.cp_pub, &v.context).expect("open must succeed");
        assert_eq!(opened, v.plaintext);
    }

    // THE confidentiality proof: nothing recognisable from the payload survives on the wire. Checks
    // every 8-byte window of the plaintext, so a partially-encrypted envelope would also fail.
    #[test]
    fn envelope_leaks_no_plaintext() {
        let v = vector();
        assert!(
            v.plaintext.len() >= 8,
            "vector plaintext too short to be meaningful"
        );
        assert!(
            !v.envelope
                .windows(v.plaintext.len())
                .any(|w| w == v.plaintext.as_slice()),
            "the full plaintext appears verbatim inside the envelope"
        );
        for (i, window) in v.plaintext.windows(8).enumerate() {
            assert!(
                !v.envelope.windows(8).any(|w| w == window),
                "plaintext bytes [{}..{}] appear in cleartext inside the envelope",
                i,
                i + 8
            );
        }
    }

    // A single flipped ciphertext bit must be caught by the signature, before any decryption.
    #[test]
    fn rejects_tampered_ciphertext() {
        let v = vector();
        let mut tampered = v.envelope.clone();
        let idx = HEADER_LEN + 1;
        tampered[idx] ^= 0x01;
        assert!(matches!(
            open(&tampered, &v.edge_priv, &v.cp_pub, &v.context),
            Err(HseError::BadSignature)
        ));
    }

    #[test]
    fn rejects_tampered_signature() {
        let v = vector();
        let mut tampered = v.envelope.clone();
        let last = tampered.len() - 1;
        tampered[last] ^= 0x01;
        assert!(matches!(
            open(&tampered, &v.edge_priv, &v.cp_pub, &v.context),
            Err(HseError::BadSignature)
        ));
    }

    // An envelope sealed for one edge cannot be opened by another edge's key.
    #[test]
    fn rejects_wrong_edge_key() {
        let v = vector();
        let mut other = v.edge_priv;
        other[0] ^= 0xff;
        assert!(matches!(
            open(&v.envelope, &other, &v.cp_pub, &v.context),
            Err(HseError::Decrypt)
        ));
    }

    // Impersonation: a bundle signed by anything other than the pinned control-plane key is refused.
    #[test]
    fn rejects_wrong_control_plane_key() {
        let v = vector();
        let mut other = v.cp_pub;
        other[0] ^= 0xff;
        assert!(matches!(
            open(&v.envelope, &v.edge_priv, &other, &v.context),
            Err(HseError::BadSignature)
        ));
    }

    // Context binding: an envelope sealed for rule distribution can't be opened as metrics.
    #[test]
    fn rejects_wrong_context() {
        let v = vector();
        assert!(matches!(
            open(
                &v.envelope,
                &v.edge_priv,
                &v.cp_pub,
                b"hokeka.metrics.report"
            ),
            Err(HseError::Decrypt)
        ));
    }

    #[test]
    fn rejects_malformed_framing() {
        let v = vector();
        assert!(matches!(
            open(&[0u8; 8], &v.edge_priv, &v.cp_pub, &v.context),
            Err(HseError::TooShort)
        ));

        let mut bad_magic = v.envelope.clone();
        bad_magic[0] = b'X';
        assert!(matches!(
            open(&bad_magic, &v.edge_priv, &v.cp_pub, &v.context),
            Err(HseError::BadMagic)
        ));

        let mut bad_version = v.envelope.clone();
        bad_version[4] = 99;
        assert!(matches!(
            open(&bad_version, &v.edge_priv, &v.cp_pub, &v.context),
            Err(HseError::BadVersion)
        ));

        // Declared ciphertext length that disagrees with the actual envelope size.
        let mut bad_len = v.envelope.clone();
        bad_len[49..53].copy_from_slice(&u32::to_be_bytes(0xdead));
        assert!(matches!(
            open(&bad_len, &v.edge_priv, &v.cp_pub, &v.context),
            Err(HseError::BadFraming)
        ));

        // Truncating the envelope must never yield a partial plaintext.
        let truncated = &v.envelope[..v.envelope.len() - 1];
        assert!(open(truncated, &v.edge_priv, &v.cp_pub, &v.context).is_err());
    }

    fn hex_to_bytes(s: &str) -> Vec<u8> {
        (0..s.len())
            .step_by(2)
            .map(|i| u8::from_str_radix(&s[i..i + 2], 16).unwrap())
            .collect()
    }
}
