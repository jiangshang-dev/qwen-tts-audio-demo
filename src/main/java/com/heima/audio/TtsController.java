package com.heima.audio;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class TtsController {

    private final String url;
    private final String model;
    private final String voice;

    public TtsController(@Value("${qwen.tts.url}") String url,
                         @Value("${qwen.tts.model}") String model,
                         @Value("${qwen.tts.voice}") String voice) {
        this.url = url;
        this.model = model;
        this.voice = voice;
    }

    @PostMapping(value = "/api/tts", produces = "audio/wav")
    public ResponseEntity<byte[]> synthesize(@RequestBody Map<String, String> body) {
        String text = body == null ? "" : body.getOrDefault("text", "");
        String selectedVoice = body == null ? "" : body.getOrDefault("voice", "");
        byte[] audio = TextToSpeech.synthesizeLocal(
                url,
                model,
                selectedVoice == null || selectedVoice.isBlank() ? voice : selectedVoice,
                text);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .header("Content-Disposition", "inline; filename=\"speech.wav\"")
                .body(audio);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handle(RuntimeException ex) {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("message", ex.getMessage() == null ? "文本转语音失败" : ex.getMessage()));
    }
}
