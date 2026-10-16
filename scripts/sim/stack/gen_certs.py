"""Sim PKI (idempotent). Needs `openssl` and `keytool` on PATH (Git Bash provides openssl).

  py scripts/sim/stack/gen_certs.py base            sim CA, proxy server cert (SAN localhost/127.0.0.1),
                                                     PKCS12 truststore the edge uses to trust the proxy
  py scripts/sim/stack/gen_certs.py edge <edgeId>   edge mTLS client cert (CN=<edgeId>) as PKCS12 and
                                                     the edge's own TLS server keystore for :8443
Output: scripts/sim/results/edge/pki (gitignored). Keystore password: SIM_KEYSTORE_PASSWORD or
'hokeka-sim-pw'.
"""
import os
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.abspath(os.path.join(HERE, "..", "results", "edge", "pki"))
PW = os.environ.get("SIM_KEYSTORE_PASSWORD", "hokeka-sim-pw")


def _openssl():
    exe = shutil.which("openssl")
    if not exe:
        for cand in (r"C:\Program Files\Git\usr\bin\openssl.exe", r"C:\Program Files\Git\mingw64\bin\openssl.exe"):
            if os.path.exists(cand):
                return cand
        sys.exit("openssl not found on PATH")
    return exe


def run(args):
    env = dict(os.environ)
    # Some Windows installs point OPENSSL_CONF at a missing file (e.g. psqlODBC); use a minimal one.
    env["OPENSSL_CONF"] = os.path.join(OUT, "sim-openssl.cnf")
    env["MSYS_NO_PATHCONV"] = "1"
    p = subprocess.run(args, cwd=OUT, env=env, capture_output=True, text=True)
    if p.returncode != 0:
        sys.exit(f"command failed: {' '.join(args)}\n{p.stdout}\n{p.stderr}")


def exists(name):
    return os.path.exists(os.path.join(OUT, name))


def base():
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, "sim-openssl.cnf"), "w") as fh:
        fh.write("[req]\ndistinguished_name=dn\n[dn]\n")
    ossl = _openssl()
    if not exists("ca.crt"):
        run([ossl, "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "30", "-keyout", "ca.key",
             "-out", "ca.crt", "-subj", "/CN=Hokeka Sim CA/O=Hokeka Sim",
             "-addext", "basicConstraints=critical,CA:TRUE", "-addext", "keyUsage=critical,keyCertSign,cRLSign"])
    if not exists("proxy-server.crt"):
        run([ossl, "req", "-newkey", "rsa:2048", "-nodes", "-keyout", "proxy-server.key", "-out", "proxy-server.csr",
             "-subj", "/CN=localhost/O=Hokeka Sim"])
        with open(os.path.join(OUT, "proxy-server.ext"), "w") as fh:
            fh.write("subjectAltName=DNS:localhost,IP:127.0.0.1\nextendedKeyUsage=serverAuth\n")
        run([ossl, "x509", "-req", "-in", "proxy-server.csr", "-CA", "ca.crt", "-CAkey", "ca.key", "-CAcreateserial",
             "-days", "30", "-extfile", "proxy-server.ext", "-out", "proxy-server.crt"])
    if not exists("truststore.p12"):
        run(["keytool", "-importcert", "-noprompt", "-alias", "sim-ca", "-file", "ca.crt", "-keystore",
             "truststore.p12", "-storetype", "PKCS12", "-storepass", PW])
    print("base PKI ready in", OUT)


def edge(edge_id):
    ossl = _openssl()
    if not exists(f"edge-client-{edge_id}.p12"):
        run([ossl, "req", "-newkey", "rsa:2048", "-nodes", "-keyout", f"edge-client-{edge_id}.key",
             "-out", f"edge-client-{edge_id}.csr", "-subj", f"/CN={edge_id}/O=Hokeka Edge"])
        with open(os.path.join(OUT, "edge-client.ext"), "w") as fh:
            fh.write("extendedKeyUsage=clientAuth\n")
        run([ossl, "x509", "-req", "-in", f"edge-client-{edge_id}.csr", "-CA", "ca.crt", "-CAkey", "ca.key",
             "-CAcreateserial", "-days", "30", "-extfile", "edge-client.ext", "-out", f"edge-client-{edge_id}.crt"])
        run([ossl, "pkcs12", "-export", "-in", f"edge-client-{edge_id}.crt", "-inkey", f"edge-client-{edge_id}.key",
             "-name", "edge-client", "-out", f"edge-client-{edge_id}.p12", "-passout", f"pass:{PW}"])
    if not exists(f"edge-tls-{edge_id}.p12"):
        run(["keytool", "-genkeypair", "-alias", "edge", "-keyalg", "EC", "-groupname", "secp256r1", "-validity", "30",
             "-dname", f"CN=hokeka-edge,O=Hokeka Edge,OU={edge_id}", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
             "-keystore", f"edge-tls-{edge_id}.p12", "-storetype", "PKCS12", "-storepass", PW, "-keypass", PW])
    print("edge PKI ready for", edge_id)
    return {
        "client_p12": os.path.join(OUT, f"edge-client-{edge_id}.p12"),
        "tls_p12": os.path.join(OUT, f"edge-tls-{edge_id}.p12"),
        "truststore": os.path.join(OUT, "truststore.p12"),
        "ca": os.path.join(OUT, "ca.crt"),
        "password": PW,
    }


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "base"
    base()
    if cmd == "edge":
        edge(sys.argv[2])
