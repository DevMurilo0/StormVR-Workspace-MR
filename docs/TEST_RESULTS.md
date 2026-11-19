# Verificação — v0.1.0-alpha — 2026-11-18

## Compilação real

Retomada no commit `dbc19ad`, preservando a branch `feature/workspace-mr` e recuperando todo o histórico anterior. O erro CXX1101 foi resolvido pela instalação íntegra do NDK oficial 27.0.12077973.

Comando executado no Arch Linux:

```bash
bash tools/workspace/build-android.sh :app:testDebugUnitTest :app:assembleDebug --console=plain
```

**BUILD SUCCESSFUL in 11m 56s — 69 actionable tasks: 69 executed.** Kotlin, Java, AIDL, recursos, DEX e JNI foram compilados. O JNI foi gerado para `arm64-v8a` e `armeabi-v7a`. Há avisos de APIs obsoletas e variáveis não utilizadas, sem falhas de compilação. A segunda compilação de `:app:assembleDebug`, com filtro ARM e NDK fixado no módulo app, terminou em **BUILD SUCCESSFUL in 35s**, com 7 tarefas executadas e 56 atualizadas. O empacotamento removeu os símbolos nativos corretamente; o APK final tem aproximadamente 90 MiB e somente as duas ABIs ARM.

Ferramentas: Temurin Java 17.0.21.1, Gradle 8.7, AGP 8.5.2, Kotlin 1.9.24; app compile/target SDK 34, Moonlight compile SDK 35, Build Tools 34.0.0, NDK 27.0.12077973 / Clang 18.0.1. O módulo JNI usa ndk-build; CMake não é necessário para o APK.

APK final: `StormVR-Workspace-MR-v0.1.0-alpha.apk` (93876597 bytes). SHA-256: `b32a7b717be3f8090624e7efe5cbedfbe8752b7bf14e2383463086815df56897`. Cópia local: `/home/murilo/StormVR-entrega/v0.1.0-alpha/`

## Resultados

| Verificação | Resultado |
|---|---|
| NDK anteriormente incompleto | Confirmado: havia somente `.installer`, sem `source.properties` |
| Arquivo oficial NDK | SHA-1 `5e5cd517bdb98d7e0faf2c494a3041291e71bdcc` conferido; CRC completo do ZIP aprovado |
| NDK instalado | Revisão exata em `source.properties`; links simbólicos oficiais restaurados; Clang e GNU Make executados; nenhum link quebrado encontrado |
| Plataformas 34/35 e platform-tools | Downloads conferidos com checksums do índice oficial Google |
| SDK/caches | `/home/murilo/Android/stormvr`; `local.properties` local aponta para `sdk` nesse diretório; sem instalação global na raiz |
| Cinco testes JUnit WorkspaceGeometryTest | **5 testes, 0 falhas, 0 erros, 0 ignorados** |
| Oito testes Python do serviço Linux | **8 testes aprovados** |
| Diagnóstico Linux `check-host.sh` | Código 0; portal ScreenCast, GStreamer/PipeWire e monitor KDE disponíveis |
| `py_compile`, sintaxe dos scripts shell e `git diff --check` | Aprovados |
| Classes do Workspace no DEX | WorkspaceManager, WorkspaceDisplays, WorkspaceInteraction, WorkspacePanel, CameraPassthrough e MoonlightSession presentes |
| Identidade do APK | `com.stormvr.workspace.mr`, versão `0.1.0-alpha`, mínimo API 26; rótulo StormVR Workspace MR |
| Assinatura do APK | Verificada por apksigner, APK Signature Scheme v2, um signatário debug |
| Limpeza da árvore Git | Nenhum build, cache Gradle, `.cxx`, `dist` ou local.properties rastreado na revisão atual |
| Busca de credenciais | Nenhum candidato por nomes de arquivos de chaves no histórico; nenhum padrão comum de token/chave privada encontrado nos arquivos atuais examinados |
| Dispositivo Android | `adb devices -l`: lista vazia. Nenhum APK instalado ou removido nesta sessão |

Os testes Python verificam autenticação, fonte inexistente, expiração de frames, metadados, duas fontes com conteúdo distinto, página sem credencial embutida, TLS com certificado verificado e pipeline GStreamer com JPEG real. Usam fontes de teste; **não comprovam captura do desktop real por portal**. O teste JUnit verifica identidade/geometria, órbita e interseção de raios, sem simular câmera ou GPU Android.

## Revisão das integrações e testes físicos pendentes

| Integração | Revisão e limite da evidência |
|---|---|
| Câmera/passthrough | ARCore preservado; Camera2 alternativo com permissão conferida e ARCore pausado antes do fallback. Não testado em aparelho |
| Estéreo/VR Box | Renderização por olho e distorção existentes preservadas e compiladas. FOV, IPD, alinhamento e conforto não validados |
| MediaPipe/pinça | Pipeline ARCore existente compartilhado; modelo baixado no primeiro uso (~8 MB). Sem mãos no fallback Camera2 |
| Manipulação espacial | Raios, pinça, mover, redimensionar e duas mãos compilados; cinco testes de geometria passaram. Ergonomia exige aparelho |
| Múltiplas telas | Até três totais, com VirtualDisplay/WebView/textura por adicional. Memória/FPS e conteúdo real simultâneo não medidos |
| Moonlight/Sunshine/OpenGL | SurfaceTexture OES consumida na thread GL antes dos dois olhos. Callbacks de sessões encerradas e reconexões protegidos por geração; watchdog interrompido ao parar |
| Congelamento na Sala VR | Caminho de composição e ciclo de vida revisados; **não reproduzido nem confirmado como corrigido em aparelho** |
| Persistência | Serialização de layouts versionada e compilada. Salvar/carregar durante uma sessão real ainda precisa de teste |
| Serviço Linux | Portal/PipeWire, consentimento, autenticação e JPEG/HTTPS implementados; testes sintéticos passaram. Seleção real de monitores depende da janela interativa do portal |

## Limitações conhecidas

- Uma única sessão Moonlight; telas adicionais usam páginas independentes ou JPEG/HTTPS, a até 15 FPS, sem H.264 multistream.
- Serviço JPEG sem injeção remota de mouse/teclado. Use os periféricos do PC.
- Camera2 monocular duplicado para os olhos, com 3DoF por IMU, sem mãos e sem rastreamento de posição. Não equivale a visão estéreo real.
- Layouts restauram coordenadas locais, sem reconhecer âncoras físicas persistentes do quarto.
- Criação e captura de vários monitores virtuais, desempenho, temperatura, bateria e reconexão precisam de testes integrados.
- O APK usa chave debug local, não publicada. Atualizações devem ser assinadas com a mesma chave.
- O LICENSE original é AGPL-3.0; o README original menciona GPL-3.0. Textos/créditos foram preservados, com divergência documentada.
- Commits antigos contêm builds e caches herdados. Eles não estão na árvore atual nem no pacote de fontes limpo; o histórico não foi reescrito.

## Evidências locais e reprodução

Logs: `$HOME/Android/stormvr/build.log`, `build-final.log`, `linux-tests.log` e `linux-host-check.log`. Resultado JUnit: `Source/android/app/build/test-results/testDebugUnitTest/TEST-com.stormvr.app.vrroom.WorkspaceGeometryTest.xml`.

```bash
source "$HOME/Android/stormvr/environment.sh"
bash tools/workspace/build-android.sh :app:testDebugUnitTest :app:assembleDebug
python -m unittest discover -s tools/workspace -p 'test_*.py' -v
```

No celular, testar concessão/recusa de câmera; ARCore e Camera2; recenter; estéreo/FOV/IPD/distorção; segundo plano e perda de contexto; desconexão/reconexão Wi-Fi; contador Moonlight crescendo com mãos ativas; uma/duas/três telas; fontes Linux diferentes e salvar/carregar layout. Registrar modelo/API, codec, logcat, FPS, memória e temperatura. Nenhum desses testes físicos é considerado aprovado por esta compilação.
