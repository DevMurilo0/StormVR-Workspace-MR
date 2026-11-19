# StormVR Workspace MR — v0.1.0-alpha

Derivado independente e experimental do [StormVR, criado por OLUKE](https://github.com/oluke0f-art/StormVR). Preserva o desenvolvimento da branch `feature/workspace-mr`, incluindo o commit `dbc19ad`. Não envia alterações ao projeto original. Os créditos e a apresentação original estão em [README_UPSTREAM.md](README_UPSTREAM.md).

## Funcionalidades e limites

- Sala VR estéreo para VR Box, passthrough ARCore e alternativa Camera2 monocular.
- Rastreamento MediaPipe e pinça pelo pipeline ARCore; Camera2 não oferece mãos nem 6DoF. O modelo de mãos (~8 MB) é baixado no primeiro uso e exige internet.
- Até três telas: principal com Moonlight e duas WebViews independentes, manipuláveis por raios/gestos.
- Presets, posição, tamanho, curvatura, visibilidade, bloqueio e layouts locais persistentes.
- Vídeo Moonlight diretamente em textura OpenGL OES, com reconexão e proteção contra callbacks de sessões encerradas.
- Serviço Linux com seleção explícita de fontes via portal ScreenCast/PipeWire, autenticação e JPEG por HTTPS.

Há **uma sessão Moonlight**, e o serviço alternativo opera a até 15 FPS, sem entrada remota Linux. Não há comprovação de correção do congelamento em aparelho. Câmera, mãos, reconexão, desempenho e múltiplos monitores precisam de testes físicos. Consulte [funcionalidades e limitações detalhadas](docs/WORKSPACE_MR.md) e [resultados dos testes](docs/TEST_RESULTS.md).

## Pastas

| Caminho | Conteúdo |
|---|---|
| `Source/android` | App Kotlin/Java e núcleo Moonlight/JNI |
| `tools/workspace` | Serviço Linux, TLS, instalação, testes e ferramentas Android |
| `Source/linux_droiddeck` | Driver e ferramentas Linux herdados |
| `docs` | Guia técnico, testes e limitações |
| Demais pastas em `Source` | Componentes originais preservados |

## Compilar no Linux

Use Java 17, Gradle 8.7, AGP 8.5.2, Kotlin 1.9.24, SDK 34/35, Build Tools 34 e NDK **27.0.12077973**. O bootstrap instala em `$HOME/Android/stormvr`, verifica os downloads e exige pelo menos 10 GiB livres. O argumento de instalação aceita as licenças do SDK Android.

```bash
git clone https://github.com/DevMurilo0/StormVR-Workspace-MR.git
cd StormVR-Workspace-MR
python tools/workspace/bootstrap-android.py --accept-licenses
source "$HOME/Android/stormvr/environment.sh"
bash tools/workspace/build-android.sh :app:testDebugUnitTest :app:assembleDebug
```

Saída: `Source/android/app/build/outputs/apk/debug/app-debug.apk`. O NDK inclui o `ndk-build`; CMake não é usado pelo módulo Android. SDK, NDK, JDK e caches permanecem no `/home`. `local.properties` é específico da máquina e não é versionado.

## Instalar no Android

Requisitos mínimos declarados: Android 8/API 26, ARM64 ou ARMv7, giroscópio e OpenGL ES 2.0. ARCore e os recursos da câmera dependem do aparelho. Baixe o APK da pré-release `v0.1.0-alpha`, permita a instalação por essa fonte e abra **StormVR Workspace MR**.

Ou, com depuração USB autorizada:

```bash
"$ANDROID_HOME/platform-tools/adb" devices -l
"$ANDROID_HOME/platform-tools/adb" install -r StormVR-Workspace-MR-v0.1.0-alpha.apk
```

O pacote `com.stormvr.workspace.mr` possui dados e pareamento próprios e coexiste com `com.stormvr.app`. Não desinstale o StormVR original. Builds locais futuros precisam usar a mesma chave debug para atualizar esta instalação; as chaves não são publicadas.

## Configurar o Linux e as telas

1. Mantenha Sunshine funcionando no PC e conecte PC e celular à mesma LAN. No app, abra Moonlight e pareie confirmando o PIN no Sunshine. Comece com 720p30. Na Sala VR, abra Home → Moonlight → Desktop.
2. Para fontes adicionais, confira as dependências do portal/GStreamer e instale o lançador:

```bash
bash tools/workspace/check-host.sh
bash tools/workspace/install-service.sh
bash tools/workspace/setup-tls.sh 192.168.1.20
stormvr-workspace --bind 192.168.1.20 \
  --cert "$HOME/.config/stormvr-workspace/cert.pem" \
  --key "$HOME/.config/stormvr-workspace/key.pem" \
  --token-file "$HOME/.config/stormvr-workspace/token" --fps 10
```

Substitua o IP pelo endereço privado do PC. O lançador é instalado em `~/.local/bin`; se necessário, use `~/.local/bin/stormvr-workspace`. Selecione os monitores na janela do portal. Nenhum serviço inicia automaticamente.

3. Instale apenas a CA `stormvr-lan.crt` no Android após comparar sua impressão digital. Configure uma tela adicional com `https://IP_DO_PC:8443/#TOKEN_LOCAL`, selecione uma fonte e repita com outra fonte em outra tela. Nunca transfira a chave privada. Os detalhes e dependências Arch estão no [guia](docs/WORKSPACE_MR.md#fontes-linux-independentes-por-portalpipewire).

## Atualizações e testes

```bash
git switch main
git pull --ff-only
source "$HOME/Android/stormvr/environment.sh"
bash tools/workspace/build-android.sh :app:testDebugUnitTest :app:assembleDebug
python -m unittest discover -s tools/workspace -p 'test_*.py' -v
git log --oneline -10
```

A branch `feature/workspace-mr` permanece disponível. A release é uma versão de desenvolvimento assinada com chave debug; não há teste físico implícito na compilação.

## Licenças e atribuição

StormVR original: OLUKE / `oluke0f-art`. [LICENSE](LICENSE) contém **GNU AGPL-3.0**, embora o README original declare GPL-3.0. O texto e os avisos originais são preservados, sem converter a licença para uma modalidade mais permissiva. A divergência deve ser esclarecida com o titular antes de uma distribuição pública. As obrigações de disponibilização do código aplicável permanecem; o código correspondente acompanha a tag da release.

O núcleo Moonlight/Artemis e suas dependências mantêm seus créditos e licenças nos diretórios originais. As bibliotecas estáticas herdadas são necessárias ao JNI; sua procedência está em `Source/android/moonlightcore/src/main/jni/moonlight-core/Build.txt` e o script OpenSSL acompanha o código. Caches e builds foram retirados da árvore atual; os commits antigos são preservados para não reescrever o histórico.
