
```angular2html
mlx_audio.server --host 0.0.0.0 --port 9000

curl -X POST http://127.0.0.1:9000/v1/audio/speech \
  -H "Content-Type: application/json" \
  -d '{
    "model": "mlx-community/Qwen3-TTS-12Hz-1.7B-CustomVoice-8bit",
    "input": "你好，我是龙宝。",
    "voice": "Vivian",
    "response_format": "mp3"
  }' \
  --output speech.mp3
```

下载mlx-community/Qwen3-TTS-12Hz-1.7B-CustomVoice-8bit

