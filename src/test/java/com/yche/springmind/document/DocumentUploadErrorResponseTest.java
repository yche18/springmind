package com.yche.springmind.document;

import com.yche.springmind.auth.security.JwtAccessTokenService;
import com.yche.springmind.common.exception.GlobalExceptionHandler;
import com.yche.springmind.document.controller.DocumentController;
import com.yche.springmind.document.service.DocumentService;
import com.yche.springmind.document.service.DocumentUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@Import(GlobalExceptionHandler.class)
class DocumentUploadErrorResponseTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentService documentService;

    @MockBean
    private DocumentUploadService documentUploadService;

    @MockBean
    private JwtAccessTokenService jwtAccessTokenService;

    @Test
    void shouldReturnApiResponseWhenUploadExceedsMultipartLimit() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "too-large.txt",
                "text/plain",
                "ab".getBytes()
        );
        given(documentService.uploadDocument(any(), any()))
                .willThrow(new MaxUploadSizeExceededException(10L));

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("groupId", "2001"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("上传文件超过大小限制"));
    }
}
