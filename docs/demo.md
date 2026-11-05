# 千问文本转语音演示

浏览器里输入文字，选择音色和语气，由本机的 mlx-audio 合成 WAV 并直接播放。密钥和模型都留在本机，页面不访问云端。

当前模型是 `mlx-community/Qwen3-TTS-12Hz-1.7B-CustomVoice-8bit`。它只能使用模型里写好的 9 个说话人，不能再安装东北话、粤语、闽南语、台语、上海话。

## 这台电脑的硬件

下面是这台机器的实测配置，也是这份演示跑通时的环境。

| 项目 | 配置 |
| --- | --- |
| 芯片 | Apple M5 Pro |
| CPU | 系统报告 15 个线程 |
| 内存 | 48 GB 统一内存 |
| 系统 | macOS 26.7.1（Build 25G241） |
| 模型占用磁盘 | 整个目录约 2.9 GB，其中语音解码器 `speech_tokenizer` 约 651 MB |

mlx-audio 依赖 Apple 的 Metal，只能在 Apple 芯片的 Mac 上跑。这个 1.7B 8bit 模型加语音解码器大约 2.9 GB，运行时还要再占一块统一内存。

### 低于这些配置不考虑

本地这条链路，下面任一条件都不纳入：

| 条件 | 原因 |
| --- | --- |
| 不是 Apple 芯片还想跑 mlx-audio | Intel Mac、Windows、Linux 没有 Metal，这条 Mac 命令起不来。它们要改走下面的 NVIDIA 方案，或者直接用云端 |
| Apple 芯片但统一内存低于 16 GB | 系统本身就要占掉数 GB，8 GB 机器装得下模型文件，加载后容易被挤掉或卡死 |
| 可用磁盘低于 4 GB | 模型目录约 2.9 GB，还要留缓存，不够就下不完整 |

16 GB 统一内存、Apple 芯片、磁盘富余 4 GB 以上，才考虑在 Mac 上部署这份 mlx-audio 演示。更低的 Mac 不要在本地跑这个模型。

## Linux、Windows 怎么跑

`mlx-audio` 只属于 Apple 芯片的 Mac。Linux 和 Windows 不要安装它，也不要执行 `python3 -m mlx_audio.server`。

这两类机器跑的是千问官方 PyTorch 包 [Qwen3-TTS](https://github.com/QwenLM/Qwen3-TTS)（`pip install qwen-tts`），模型用 `Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice`。前提是有 NVIDIA 显卡和对应的 CUDA 驱动。核显、AMD、昇腾不按这套方案交付。没有 NVIDIA 显卡的 Linux 或 Windows，本地模型不考虑，直接用云端。

1.7B 半精度权重大约 3.4 GB，推理时还要额外占显存。显存低于 12 GB 的卡不纳入正式环境；8 GB 卡最多试 0.6B，不作为讲解服务。内存低于 16 GB、磁盘可用空间低于 20 GB，也不考虑。

| 用途 | 配置 | 说明 |
| --- | --- | --- |
| 开发试用 | 1 张 16 GB NVIDIA 卡（如 RTX 4060 Ti 16 GB），CPU 8 核，内存 32 GB，磁盘 100 GB SSD | 能跑 1.7B，并发很低 |
| 馆内一台长期服务 | 1 张 24 GB 卡（RTX 4090 / 4090D 或 A10），CPU 16 核，内存 64 GB，磁盘 200 GB SSD | 单机小并发 |
| 同时很多路讲解 | 不要用一张 16 GB 卡硬扛 | 加卡，或改用云端按量 |

Linux 服务器按官方方式起环境：

```bash
conda create -n qwen3-tts python=3.12 -y
conda activate qwen3-tts
pip install -U qwen-tts
```

然后再安装和本机驱动匹配的 CUDA 版 PyTorch。`nvidia-smi` 能看到显卡之后，才能加载模型。官方示例是 `Qwen3TTSModel.from_pretrained("Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice", device_map="cuda:0", dtype=torch.bfloat16)`，说话人仍然是 Vivian、Eric 这一套。

Windows 实体机优先用 WSL2 里的 Ubuntu，装好 Windows 上的 NVIDIA 驱动后，在 WSL2 里走上面同一套命令。原生 Windows 也能装 CUDA 版 PyTorch，但 FlashAttention 的安装轮子更容易对不上，不作为首选。

现在的演示页面只会请求 mlx-audio 的 `POST /v1/audio/speech`。Linux 和 Windows 上的官方包没有这个 HTTP 服务。要继续用这个页面，需要另写一个提供同样接口的 Python 服务，把 `input`、`voice`、`instruct` 转给 `qwen-tts`。只改 `application.yml` 里的地址，页面不会自动在 Linux 上出声。

### 成本大概多少

价格用的是 2026 年 11 月还能看到的公开标价，库存和折扣会变，以下单页为准。

云端不用买显卡。`qwen-audio-3.1-tts-flash` 在模型页上的标价是输入约 1.5 元/百万 token、输出约 12 元/百万 token。讲解量不大时，按月往往比养一台 GPU 便宜；具体一个月多少钱以账单为准，不能按文字字数直接换算。

租一台 NVIDIA 机器，适合先验证再决定买不买：

| 机器 | 公开标价 | 适合 |
| --- | --- | --- |
| RTX 4060 16 GB 云主机 | 约 0.74 元/小时，约 425 元/月 | 试用 1.7B |
| RTX 4090D | 国内平台监测大约 1.98 元/小时；全天开机大约 1400 元/月 | 单机服务 |
| A10 24 GB | 有的平台会员价约 3 元/小时、约 1700 元/月；另有监测约 6 元/小时 | 机房向的推理卡，报价差一倍，必须看下单页 |

自己买是一次性投入，后面主要剩电费。下面是 2026 年公开标价，国补、教育优惠和缺货都会改价，下单前再看一眼京东或供应商页面。

| 买什么 | 配置 | 大约多少钱 | 能做什么 |
| --- | --- | --- | --- |
| Mac mini（M4，16 GB 内存） | Apple 芯片，磁盘建议 512 GB，不要只买 256 GB | 16 GB / 256 GB 面价大约 3600–4500 元，国补后有人买到 3100–3800 元；16 GB / 512 GB 大约 5100–6000 元 | Mac 上跑 mlx-audio 的最低一档。256 GB 磁盘去掉系统后偏紧 |
| 现在这台档次的 Mac | M5 Pro、48 GB 统一内存 | 比 mini 高一档，属于开发机，不是入门价 | 这份演示实际跑通的机器 |
| Windows / Linux 台式机 | 1 张 16 GB 显卡（RTX 4060 Ti 16 GB 或 RTX 5060 Ti 16 GB），内存 32 GB，磁盘 1 TB | 整机活动价大约 5800–7400 元 | 试用 1.7B，并发很低 |
| Windows / Linux 工作站 | 1 张 RTX 4090D 24 GB，内存 64 GB，电源至少 1000 W | 显卡本身大约 1.35 万–1.8 万元；配成整机大约 2 万–3 万元 | 馆里长期开着的一台服务 |
| 机架服务器 | 1 张数据中心卡（如 A10 24 GB），冗余电源，上机柜 | 没有稳定的公开零售价，要比同显存的游戏整机贵，向厂商询价 | 放机房、7×24 小时开机时再买 |

电费另计。4090D 整卡功耗大约 425 W，按 0.6–1 元/度、全天开机粗算，一个月大约 200–300 元。16 GB 消费级卡功耗更低，电费更少。Mac mini 功耗通常比独显主机小。

一台机器只服务本馆、并发不高时，买一台 16 GB 显卡的台式机或一台 16 GB 内存的 Mac mini 就够。要长期开、还要留余量，买 24 GB 显卡的工作站。人多、或者必须上东北话和粤语，买机器不如继续用云端按量付费。机架服务器只在已经有机房、需要上架和冗余电源时才考虑。

LM Studio 可以下载这个模型，但不能加载它。LM Studio 的 MLX 加载器不支持 `qwen3_tts`，所以合成必须走 mlx-audio，不要在 LM Studio 里点 Load。

## 千问云端哪些语音模型能用

模型目录见 [千问 AI 平台](https://www.qianwenai.com/?source_channel=hy_qwen&utm_content=g_1000415132)，选型说明见[语音合成模型](https://platform.qianwenai.com/docs/developer-guides/speech/tts-models)。当前这个演示页只连本机 `9000` 端口的 HTTP 接口，不能把云端地址填进 `application.yml` 就直接用。云端走 WebSocket（例如 `wss://cn-beijing.maas.qianwenaiapi.com/api-ws/v1/inference`），还要业务空间的 API Key。Key 无效时握手会返回 401。

按讲解场景，优先用下面这些。表里的能力以千问平台文档为准。

| 模型 | 适合做什么 | 音色 | 自然语言语气 | 和本需求的关系 |
| --- | --- | --- | --- | --- |
| `qwen-audio-3.1-tts-flash` | 导览问答、实时对话 | 系统音色、声音复刻、声音设计 | 支持 | 优先选它。低延迟，能用指令改语气 |
| `qwen-audio-3.1-tts-plus` | 讲解成片、有声稿 | 同上 | 支持 | 更看重音质时用它 |
| `qwen-audio-3.0-tts-flash` | 上一版实时合成 | 同上 | 支持 | 3.1 可用时不必再选 |
| `qwen-audio-3.0-tts-plus` | 上一版内容制作 | 同上 | 支持 | 同上 |
| `cosyvoice-v3.5-plus` | 方言和声音设计 | 复刻、设计；系统音色文档写明不支持 | 支持 | 要东北话、上海话、粤语、闽南话时用复刻音色加指令 |
| `cosyvoice-v3.5-flash` | 同上，更偏速度 | 同上 | 支持 | 同上 |
| `cosyvoice-v3-flash` | 系统音色里直接带一部分方言 | 系统音色含普通话、广东话、东北话、四川话、闽南话等；也支持复刻 | 支持 | 不想做声音复刻、又要方言时可以先看它的系统音色 |
| `cosyvoice-v3-plus` | 音质更好的 CosyVoice | 复刻支持多种方言；系统音色主要是普通话和英文 | 不支持 | 要指令改语气就不要选它 |
| `qwen3-tts-flash` | 只要系统音色的短语音 | 仅系统音色 | 不支持 | 系统音色里按具体音色提供北京话、上海话、四川话、闽南话、粤语等，不能复刻，也不能写语气提示词 |
| `qwen3-tts-flash-realtime` | 上面这个的实时流式版 | 仅系统音色 | 不支持 | 同上，适合边出字边出声 |

文档里没有单独列出「台语」。云端写到的是闽南话，不能把它当成台语已经支持。

方言大致怎么选：

- 只要普通话、男声女声、专业 / 幽默 / 儿童语气：用 `qwen-audio-3.1-tts-flash`，系统音色即可，指令里写语气。
- 要东北话、上海话、粤语（文档里写作广东话）、四川话、闽南话：优先 `cosyvoice-v3.5-plus` 或 `cosyvoice-v3.5-flash` 的复刻音色，再用指令指定方言；或者看 `cosyvoice-v3-flash`、`qwen3-tts-flash` 的系统音色是否已经带这个方言。
- 本地 CustomVoice 只有北京话（Dylan）和四川话（Eric），没有东北话、粤语、闽南话、上海话。这些只能走云端，不能在现在的下拉框里加出来。

平台上还有 `MiniMax/speech-2.8-hd`、`MiniMax/speech-2.8-turbo`、`MiniMax/speech-02-hd`、`MiniMax/speech-02-turbo`，以及只用来登记复刻音色的 `voice-enrollment`。MiniMax 支持声音复刻，文档没有把它们标成自然语言指令控制。`cosyvoice-v2` 是旧版，新接入不要用。

云端示例音色是 `longanhuan_v3.1`，模型 ID 是 `qwen-audio-3.1-tts-flash`。这和本地的 `vivian` 不是同一套名字，不能混用。

## 软件

| 软件 | 本机版本 | 用途 |
| --- | --- | --- |
| Python | 3.11.0，路径 `/Library/Frameworks/Python.framework/Versions/3.11/bin/python3` | 跑 mlx-audio |
| mlx | 0.32.4 | Apple GPU 推理 |
| mlx-audio | 0.6.4 | 提供 `http://127.0.0.1:9000/v1/audio/speech` |
| JDK | Oracle Java 21.0.10 | 跑演示页面 |
| Maven | 本机已安装 | 启动 Spring Boot |
| ffmpeg | 未安装 | 只有要输出 MP3 时才需要 |

演示本身是 Spring Boot 3.4.5，只依赖 `spring-boot-starter-web`。页面是 `src/main/resources/static/index.html`，接口是 `POST /api/tts`。

## 模型目录必须完整

配置里的模型必须是本机目录，不要填 Hugging Face 仓库名。填仓库名时，mlx-audio 会再下一遍大约 3 GB，而且不使用 LM Studio 已经下好的文件。

```text
/Users/xiaobai/.lmstudio/models/mlx-community/Qwen3-TTS-12Hz-1.7B-CustomVoice-8bit
```

这个目录里除了 `model.safetensors`，还必须有子目录 `speech_tokenizer`，里面要有 `model.safetensors`（约 651 MB）和它的 `config.json`。没有语音解码器时，服务会先返回 HTTP 200，随后连接被掐断，页面上看到的是分块传输错误。

`speech_tokenizer` 只需要补这一份，不必重下主模型。可以从镜像下载：

```bash
DEST="$HOME/.lmstudio/models/mlx-community/Qwen3-TTS-12Hz-1.7B-CustomVoice-8bit/speech_tokenizer"
mkdir -p "$DEST"
BASE="https://hf-mirror.com/mlx-community/Qwen3-TTS-12Hz-1.7B-CustomVoice-8bit/resolve/main/speech_tokenizer"
curl -fsSL -o "$DEST/config.json" "$BASE/config.json"
curl -fsSL -o "$DEST/configuration.json" "$BASE/configuration.json"
curl -fsSL -o "$DEST/preprocessor_config.json" "$BASE/preprocessor_config.json"
curl -L --retry 3 -C - -o "$DEST/model.safetensors" "$BASE/model.safetensors"
```

下完后要重启 mlx-audio。已经加载进内存的模型不会自动补上解码器。

## 启动

先开语音服务，再开页面。两个进程都要一直留着。

```bash
python3 -m mlx_audio.server --host 0.0.0.0 --port 9000
```

另开一个终端：

```bash
cd /Users/xiaobai/Deveploer/workspace/company/lingxi/qwen-tts-audio-demo
mvn spring-boot:run
```

浏览器打开 `http://127.0.0.1:8090`。

地址和模型写在 `src/main/resources/application.yml`：

```yaml
server:
  port: 8090

qwen:
  tts:
    url: http://127.0.0.1:9000
    model: /Users/xiaobai/.lmstudio/models/mlx-community/Qwen3-TTS-12Hz-1.7B-CustomVoice-8bit
    voice: Vivian
```

改完 yml 或 Java 后要重新执行 `mvn spring-boot:run`。只改 `index.html` 时，把文件同步到 `target/classes/static/index.html` 后刷新浏览器即可。

第一次合成要先把模型装进内存，一句话可能要十几秒。模型已经在内存里之后，短句通常一两秒就能返回。

## 页面怎么用

1. 在「文本」里写要朗读的内容。提示词不会改这段文字。
2. 在「语气提示词」里选专业型、幽默型或儿童型，也可以自己改下面的句子。选「不指定」或把输入框留空，就按音色的默认语气读。
3. 在「音色」里选一个说话人。
4. 点「转语音」。按钮变成「转换中...」时不要重复点。成功后下方出现播放器。

语气示例：

| 选项 | 实际发给模型的提示词 |
| --- | --- |
| 专业型 | 用专业、沉稳、清晰的语气朗读 |
| 幽默型 | 用幽默、轻松、带点俏皮的语气说 |
| 儿童型 | 用适合儿童的活泼、亲切、语速稍慢的语气说 |

语气只影响怎么读，例如快慢和情绪。它不会把讲解词改写成儿童版，也不会换成另一个说话人。

内置音色：

| 页面显示 | 传给模型的名字 | 说明 |
| --- | --- | --- |
| Vivian · 女声 | vivian | 默认 |
| Serena · 女声 | serena | 女声 |
| Uncle_Fu · 男声 | uncle_fu | 男声 |
| Ryan · 男声 | ryan | 男声 |
| Aiden · 男声 | aiden | 男声 |
| Dylan · 北京话男声 | dylan | 北京话 |
| Eric · 四川话男声 | eric | 四川话 |
| Ono_Anna · 日语女声 | ono_anna | 日语 |
| Sohee · 韩语女声 | sohee | 韩语 |

名字必须是这 9 个之一。大小写不敏感，模型内部会转成小写。

播放器里的音频只在浏览器内存里，刷新页面就没了。这个演示不会把文件存到项目目录。mlx-audio 编码 WAV 时会在系统临时目录写一个文件，读完立刻删除。

## 接口

页面调用的是演示服务，不是 9000 端口：

```bash
curl -X POST http://127.0.0.1:8090/api/tts \
  -H "Content-Type: application/json" \
  -d '{
    "text": "你好，我是龙宝。",
    "voice": "vivian",
    "instruct": "用专业、沉稳、清晰的语气朗读"
  }' \
  --output speech.wav
```

`instruct` 可以不传。成功时响应是 `audio/wav`。

演示服务再去调用 mlx-audio：

```bash
curl -X POST http://127.0.0.1:9000/v1/audio/speech \
  -H "Content-Type: application/json" \
  -d '{
    "model": "/Users/xiaobai/.lmstudio/models/mlx-community/Qwen3-TTS-12Hz-1.7B-CustomVoice-8bit",
    "input": "你好，我是龙宝。",
    "voice": "vivian",
    "instruct": "用专业、沉稳、清晰的语气朗读",
    "response_format": "wav"
  }' \
  --output speech.wav
```

这里的 `model` 同样必须是本机目录。`response_format` 请保持 `wav`。本机没有 ffmpeg，请求 `mp3` 时服务会在已经返回 200 之后中断，客户端看不到音频。

## 常见问题

- 页面一直「转换中」，终端却在从 Hugging Face 下载：`application.yml` 里的 `model` 写成了仓库名。改成本机目录并重启两个服务。
- 返回 200 但没有音频，Java 报 `chunked transfer encoding`：多半是缺少 `speech_tokenizer`，或者请求了 MP3 却没有 ffmpeg。
- 音色不存在：只能用上面 9 个名字。
- 端口被占用：9000 上已有一个 mlx-audio，或 8090 上已有一个演示进程。不要再起一份。

## 以后怎么扩展

**加语气预设。** 改 `src/main/resources/static/index.html` 里 `id="style"` 的选项。选项的 `value` 就是发给模型的 `instruct`。选中后会填进下面的输入框，用户仍可手改。

**换成本机另一个 CustomVoice 模型。** 把 `application.yml` 的 `qwen.tts.model` 指到新目录，并确认该目录自带 `speech_tokenizer`。如果新模型的说话人名单不同，同步改页面下拉框。改完重启 Spring Boot。

**输出 MP3。** 先安装 ffmpeg（`brew install ffmpeg`），再把 `TextToSpeech.java` 里的 `response_format` 从 `wav` 改成 `mp3`，接口的 `Content-Type` 改成 `audio/mpeg`。没装 ffmpeg 之前不要改。

**接到导览后台。** 管理端讲解词页已经走另一条链路：读取模型管理里已激活的「语音合成」模型，调用 `/ai/audio/api/tts/api`，再把 WAV 上传到 MinIO。模型地址填 `http://127.0.0.1:9000`，模型名称填上面的本机目录。那个模型和这个演示页互不读取配置，换模型时两处都要改。

**想要更多方言或自定义声音。** 这个 CustomVoice 权重里没有对应说话人，提示词也造不出来。需要换官方的 Base 模型，并提供参考音频和对应文本做声音复刻；或者换一个本来就包含目标方言的模型。当前页面和 `synthesizeLocal` 没有接参考音频。

**0.6B 模型。** 1.7B 支持 `instruct`。如果换成 0.6B CustomVoice，语气提示词会被模型忽略。
