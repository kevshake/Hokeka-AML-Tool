package com.posgateway.aml.service.document;

import com.posgateway.aml.entity.merchant.MerchantDocument;
import com.posgateway.aml.repository.MerchantDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentManagementServiceTest {

    @Mock MerchantDocumentRepository repository;
    @TempDir Path uploadDir;

    @Test
    void persistsExpiryDigestDetectedTypeAndScanStatus() throws Exception {
        when(repository.save(any(MerchantDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ClamAvDocumentScanner scanner = new ClamAvDocumentScanner(false, false, "localhost", 3310, 1000);
        DocumentManagementService service = new DocumentManagementService(
                repository, scanner, uploadDir.toString(), 1024 * 1024);
        byte[] pdf = "%PDF-1.7\ncontent".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile("file", "identity.pdf", "application/pdf", pdf);
        LocalDate expiry = LocalDate.now().plusYears(2);

        MerchantDocument saved = service.uploadDocument(7L, file, "passport", expiry);

        assertEquals("PASSPORT", saved.getDocumentType());
        assertEquals("application/pdf", saved.getContentType());
        assertEquals((long) pdf.length, saved.getFileSize());
        assertEquals(64, saved.getSha256Hash().length());
        assertEquals(expiry, saved.getExpiryDate());
        assertEquals("NOT_SCANNED", saved.getMalwareScanStatus());
        assertTrue(Files.exists(Path.of(saved.getFilePath())));
    }

    @Test
    void rejectsMimeSpoofingBeforeWritingFile() {
        ClamAvDocumentScanner scanner = new ClamAvDocumentScanner(false, false, "localhost", 3310, 1000);
        DocumentManagementService service = new DocumentManagementService(
                repository, scanner, uploadDir.toString(), 1024 * 1024);
        MockMultipartFile file = new MockMultipartFile(
                "file", "identity.pdf", "application/pdf", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1});

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.uploadDocument(7L, file, "PASSPORT", null));

        assertTrue(error.getMessage().contains("does not match"));
    }

    @Test
    void rejectionRequiresReviewerReason() {
        MerchantDocument document = new MerchantDocument();
        when(repository.findById(9L)).thenReturn(java.util.Optional.of(document));
        DocumentManagementService service = new DocumentManagementService(repository,
                new ClamAvDocumentScanner(false, false, "localhost", 3310, 1000), uploadDir.toString(), 1024);

        assertThrows(IllegalArgumentException.class,
                () -> service.verifyDocument(9L, false, "reviewer", " "));
    }
}
