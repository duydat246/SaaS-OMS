package com.saasoms.common.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.saasoms.common.response.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StubController())
                .setControllerAdvice(handler)
                .build();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    @Test
    void get_wrongMethod_returns405WithAllow() throws Exception {
        mockMvc.perform(delete("/stub/search?q=x"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Phương thức không được hỗ trợ cho đường dẫn này"));
    }

    @Test
    void get_missingParam_returns400() throws Exception {
        mockMvc.perform(get("/stub/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Thiếu tham số \"q\""));
    }

    @Test
    void post_wrongContentType_returns415() throws Exception {
        mockMvc.perform(post("/stub/json").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string("Accept", containsString("application/json")))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Định dạng dữ liệu gửi lên không được hỗ trợ"));
    }

    @Test
    void upload_missingPart_returns400() throws Exception {
        mockMvc.perform(multipart("/stub/upload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Thiếu tệp \"file\""));
    }

    @Test
    void upload_notMultipart_returns400() throws Exception {
        mockMvc.perform(post("/stub/upload").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Dữ liệu tải lên không hợp lệ"));
    }

    @Test
    void handleNoResource_returns404() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleNoResource(new NoResourceFoundException(HttpMethod.GET, "api/khong-co"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("Không tìm thấy đường dẫn yêu cầu");
    }

    @Test
    void handleMultipart_returns400() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMultipart(new MultipartException("Failed to parse multipart servlet request"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Dữ liệu tải lên không hợp lệ");
    }

    @Test
    void handleMultipart_logsWarnWithException() {
        MultipartException ex = new MultipartException("Could not parse multipart servlet request");

        handler.handleMultipart(ex);

        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(logs.list.get(0).getFormattedMessage()).isEqualTo("Multipart parse failed");
        assertThat(((ThrowableProxy) logs.list.get(0).getThrowableProxy()).getThrowable()).isSameAs(ex);
    }

    /** Exception không kèm danh sách định dạng hỗ trợ thì không gửi header Accept rỗng. */
    @Test
    void handleMediaTypeNotSupported_noSupportedTypes_omitsAcceptHeader() {
        var response = handler.handleMediaTypeNotSupported(
                new org.springframework.web.HttpMediaTypeNotSupportedException("abc"));

        assertThat(response.getStatusCode().value()).isEqualTo(415);
        assertThat(response.getHeaders().containsKey("Accept")).isFalse();
    }

    /** Thân lỗi phải đọc được dù client chỉ nhận XML: không có converter XML cho ApiResponse. */
    @Test
    void get_acceptXml_returns406WithJsonBody() throws Exception {
        mockMvc.perform(get("/stub/map").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Định dạng phản hồi yêu cầu không được hỗ trợ"));

        assertThat(logs.list).isEmpty();
    }

    @Test
    void get_acceptXmlOnProducesMapping_returns406WithJsonBody() throws Exception {
        mockMvc.perform(get("/stub/produces").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Định dạng phản hồi yêu cầu không được hỗ trợ"));
    }

    @Test
    void get_unparsableAccept_returns406WithJsonBody() throws Exception {
        mockMvc.perform(get("/stub/map").header("Accept", "abc"))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Định dạng phản hồi yêu cầu không được hỗ trợ"));
    }

    @Test
    void maxUploadSize_keepsOwnHandlerMessage() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new OversizeController())
                .setControllerAdvice(handler)
                .build();

        mvc.perform(get("/stub/oversize"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ảnh tối đa 5MB"));

        assertThat(logs.list).isEmpty();
    }

    @RestController
    static class StubController {

        @GetMapping("/stub/search")
        String search(@RequestParam("q") String q) {
            return q;
        }

        @GetMapping("/stub/map")
        Map<String, String> map() {
            return Map.of("k", "v");
        }

        @GetMapping(path = "/stub/produces", produces = MediaType.APPLICATION_JSON_VALUE)
        Map<String, String> produces() {
            return Map.of("k", "v");
        }

        @PostMapping("/stub/json")
        Map<String, String> json(@RequestBody Map<String, String> body) {
            return body;
        }

        @PostMapping("/stub/upload")
        String upload(@RequestParam("file") MultipartFile file) {
            return file.getName();
        }
    }

    @RestController
    static class OversizeController {

        @GetMapping("/stub/oversize")
        String oversize() {
            throw new MaxUploadSizeExceededException(1L);
        }
    }
}
