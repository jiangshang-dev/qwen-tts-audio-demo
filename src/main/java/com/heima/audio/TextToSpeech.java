package com.heima.audio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 按千问 SpeechSynthesizer 的 WebSocket 协议把文字合成 MP3。
 */
public final class TextToSpeech {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final long START_TIMEOUT_SECONDS = 20;
    private static final long FINISH_TIMEOUT_SECONDS = 120;

    private TextToSpeech() {}

    /**
     * 调用本机 mlx_audio.server 的 OpenAI 兼容接口，返回 WAV。本机没有 ffmpeg，不能编码 MP3
     */
    public static byte[] synthesizeLocal(String baseUrl, String model, String voice, String text) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("本地语音服务地址不能为空");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("本地语音模型不能为空");
        }
        if (voice == null || voice.isBlank()) {
            throw new IllegalArgumentException("请指定音色");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("请输入要转换的文字");
        }

        String root = baseUrl.trim().replaceAll("/+$", "");
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model.trim());
        body.put("input", text.trim());
        body.put("voice", voice.trim());
        body.put("response_format", "wav");
        byte[] json = body.toString().getBytes(StandardCharsets.UTF_8);

        // mlx-audio 是 HTTP/1.1 服务。Java 默认协商 HTTP/2 时，对方收不到 JSON 正文。
        HttpRequest request = HttpRequest.newBuilder(URI.create(root + "/v1/audio/speech"))
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(Duration.ofMinutes(10))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json))
                .build();
        try {
            HttpResponse<byte[]> response = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(CONNECT_TIMEOUT)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                String detail = new String(response.body(), StandardCharsets.UTF_8);
                throw new IllegalStateException("本地语音服务返回 HTTP " + response.statusCode()
                        + (detail.isBlank() ? "" : "：" + detail));
            }
            if (response.body().length == 0) {
                throw new IllegalStateException("本地语音服务没有返回音频");
            }
            return response.body();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("调用本地语音服务失败：" + rootMessage(ex), ex);
        }
    }

    public static byte[] synthesize(String websocketUrl, String apiKey, String model, String voice, String text) {
        if (websocketUrl == null || websocketUrl.isBlank()) {
            throw new IllegalArgumentException("文本转语音地址不能为空");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("文本转语音密钥不能为空");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("文本转语音模型不能为空");
        }
        if (voice == null || voice.isBlank()) {
            throw new IllegalArgumentException("文本转语音音色不能为空");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("请输入要转换的文字");
        }

        URI uri = URI.create(websocketUrl.trim());
        String scheme = uri.getScheme();
        if (!"ws".equalsIgnoreCase(scheme) && !"wss".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("文本转语音地址必须以 ws 或 wss 开头");
        }

        String taskId = UUID.randomUUID().toString();
        Session session = new Session();
        WebSocket socket;
        try {
            socket = HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .build()
                    .newWebSocketBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .header("Authorization", "bearer " + apiKey.trim())
                    .buildAsync(uri, session)
                    .get(CONNECT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("连接文本转语音服务失败：" + rootMessage(ex), ex);
        }

        try {
            socket.sendText(taskMessage("run-task", taskId, model.trim(), voice.trim(), null), true).join();
            session.started().get(START_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            socket.sendText(taskMessage("continue-task", taskId, null, null, text.trim()), true).join();
            socket.sendText(taskMessage("finish-task", taskId, null, null, null), true).join();
            byte[] audio = session.finished().get(FINISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (audio.length == 0) {
                throw new IllegalStateException("文本转语音没有返回音频");
            }
            return audio;
        } catch (TimeoutException ex) {
            throw new IllegalStateException("文本转语音超时", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("文本转语音失败：" + rootMessage(ex), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("文本转语音被中断", ex);
        } finally {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        }
    }

    private static String taskMessage(String action, String taskId, String model, String voice, String text) {
        ObjectNode root = JSON.createObjectNode();
        ObjectNode header = root.putObject("header");
        header.put("action", action);
        header.put("task_id", taskId);
        header.put("streaming", "duplex");

        ObjectNode payload = root.putObject("payload");
        if ("run-task".equals(action)) {
            payload.put("task_group", "audio");
            payload.put("task", "tts");
            payload.put("function", "SpeechSynthesizer");
            payload.put("model", model);
            ObjectNode parameters = payload.putObject("parameters");
            parameters.put("text_type", "PlainText");
            parameters.put("voice", voice);
            parameters.put("format", "mp3");
            parameters.put("sample_rate", 24000);
            parameters.put("volume", 50);
            parameters.put("rate", 1.0);
            parameters.put("pitch", 1.0);
            payload.putObject("input");
        } else if (text != null) {
            payload.putObject("input").put("text", text);
        } else {
            payload.putObject("input");
        }
        return root.toString();
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && (current.getMessage() == null || current instanceof ExecutionException)) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static final class Session implements WebSocket.Listener {

        private final StringBuilder text = new StringBuilder();
        private final ByteArrayOutputStream audio = new ByteArrayOutputStream();
        private final CompletableFuture<Void> started = new CompletableFuture<>();
        private final CompletableFuture<byte[]> finished = new CompletableFuture<>();

        private CompletableFuture<Void> started() {
            return started;
        }

        private CompletableFuture<byte[]> finished() {
            return finished;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                String message = text.toString();
                text.setLength(0);
                handle(message);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            audio.writeBytes(bytes);
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (!finished.isDone()) {
                fail("文本转语音连接已关闭：" + statusCode + (reason == null || reason.isBlank() ? "" : " " + reason));
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            started.completeExceptionally(error);
            finished.completeExceptionally(error);
        }

        private void handle(String message) {
            try {
                JsonNode root = JSON.readTree(message);
                JsonNode header = root.path("header");
                String event = header.path("event").asText("");
                appendEmbeddedAudio(root);
                if ("task-started".equals(event)) {
                    started.complete(null);
                    return;
                }
                if ("task-finished".equals(event)) {
                    finished.complete(audio.toByteArray());
                    return;
                }
                if ("task-failed".equals(event)) {
                    String reason = header.path("error_message").asText("");
                    if (reason.isBlank()) {
                        reason = root.path("payload").path("message").asText("文本转语音任务失败");
                    }
                    fail(reason);
                }
            } catch (Exception ex) {
                fail("文本转语音返回内容无法解析");
            }
        }

        private void appendEmbeddedAudio(JsonNode root) {
            JsonNode audioNode = root.path("payload").path("output").path("audio");
            if (!audioNode.isTextual() || audioNode.asText().isBlank()) {
                return;
            }
            audio.writeBytes(Base64.getDecoder().decode(audioNode.asText()));
        }

        private void fail(String reason) {
            IllegalStateException error = new IllegalStateException(reason);
            started.completeExceptionally(error);
            finished.completeExceptionally(error);
        }
    }
}
