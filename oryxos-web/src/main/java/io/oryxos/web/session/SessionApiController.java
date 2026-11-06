package io.oryxos.web.session;

import io.oryxos.web.api.ApiResponse;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 连续 Web Session 的 REST 适配器。 */
@RestController
@RequestMapping(value = "/api/v1/sessions", produces = "application/json")
public final class SessionApiController {

    private final SessionWebService service;

    public SessionApiController(SessionWebService service) {
        this.service = service;
    }

    @PostMapping
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "复用已有活动会话",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(ref = "#/components/schemas/ApiResponseSessionView"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "已创建新的活动会话",
                    headers = @Header(name = "Location", description = "新会话的 URI",
                            schema = @Schema(type = "string")),
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(ref = "#/components/schemas/ApiResponseSessionView")))})
    public ResponseEntity<ApiResponse<SessionView>> create(
            @Valid @RequestBody CreateSessionRequest request) {
        var creation = service.create(request.profileName(), request.userId());
        var body = ApiResponse.success(creation.session());
        if (creation.created()) {
            return ResponseEntity.created(URI.create("/api/v1/sessions/" + creation.session().id()))
                    .body(body);
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<MessageReplyView> send(@PathVariable String id,
            @Valid @RequestBody MessageRequest request) {
        return ApiResponse.success(service.send(id, request.message()));
    }

    @GetMapping("/{id}")
    public ApiResponse<SessionView> get(@PathVariable String id) {
        return ApiResponse.success(service.get(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<SessionArchiveView> archive(@PathVariable String id) {
        return ApiResponse.success(service.archive(id));
    }
}
