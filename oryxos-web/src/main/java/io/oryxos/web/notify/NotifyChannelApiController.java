package io.oryxos.web.notify;

import io.oryxos.core.notify.NotifyChannel;
import io.oryxos.core.notify.NotifyChannelService;
import io.oryxos.web.api.ApiResponse;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/notify-channels", produces = "application/json")
public class NotifyChannelApiController {

    private final NotifyChannelService service;

    private final NotifyChannelMasker masker;

    public NotifyChannelApiController(NotifyChannelService service, NotifyChannelMasker masker) {
        this.service = service;
        this.masker = masker;
    }

    @PostMapping
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
            description = "已创建通知渠道",
            headers = @Header(name = "Location", description = "新渠道的 URI",
                    schema = @Schema(type = "string")),
            content = @Content(mediaType = "application/json",
                    schema = @Schema(ref = "#/components/schemas/ApiResponseNotifyChannelView")))
    public ResponseEntity<ApiResponse<NotifyChannelView>> create(
            @Valid @RequestBody CreateNotifyChannelRequest request) {
        NotifyChannel channel = service.create(new NotifyChannelService.CreateCommand(
                request.name(), request.type(), request.url(), request.description(),
                request.config() == null ? Map.of() : request.config()));
        return ResponseEntity.created(URI.create("/api/v1/notify-channels/" + channel.name()))
                .body(ApiResponse.success(masker.view(channel)));
    }

    @GetMapping
    public ApiResponse<List<NotifyChannelView>> list() {
        return ApiResponse.success(service.list().stream().map(masker::view).toList());
    }

    @GetMapping("/{name}")
    public ApiResponse<NotifyChannelView> get(@PathVariable("name") String name) {
        return ApiResponse.success(masker.view(service.get(name)));
    }

    @PutMapping("/{name}")
    public ApiResponse<NotifyChannelView> update(@PathVariable("name") String name,
            @Valid @RequestBody UpdateNotifyChannelRequest request) {
        NotifyChannel existing = service.get(name);
        NotifyChannelMasker.MergedUpdate merged = masker.merge(existing, request.url(), request.config());
        NotifyChannel updated = service.update(name, new NotifyChannelService.UpdateCommand(
                request.type(), merged.url(), request.description(), merged.config()));
        return ApiResponse.success(masker.view(updated));
    }

    @DeleteMapping("/{name}")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204",
            description = "已删除通知渠道")
    public ResponseEntity<Void> delete(@PathVariable("name") String name) {
        service.delete(name);
        return ResponseEntity.noContent().build();
    }
}
