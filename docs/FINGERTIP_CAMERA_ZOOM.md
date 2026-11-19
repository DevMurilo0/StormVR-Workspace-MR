# Indicador e zoom da câmera

## Usar as telas

Em **Mãos e 6DoF → Modo de interação das mãos**, ou nas preferências do Workspace:

- **Indicador (recomendado)** é o padrão. O raio passa pela ponta do indicador (landmark 8). Mantenha o indicador estendido sobre a barra superior ou um canto até completar o progresso; o padrão é 600 ms. Depois mova a mão mantendo o indicador estendido. Dobre o indicador por aproximadamente 100 ms para soltar. Não há uma segunda confirmação para mover ou redimensionar.
- **Pinça (clássico)** mantém os gatilhos e arrastes anteriores. Gamepad e controles Bluetooth continuam disponíveis.
- **Permanência** seleciona conteúdo, botões e teclas; não transforma uma permanência na moldura em movimentação. Para manipular a tela escolha Indicador ou Pinça. Sem mãos, aproveita o ponteiro por olhar existente.

Botões, links e teclas recebem um clique único após 0,8–1,2 s de estabilidade, conforme a preferência já existente. No modo Indicador não é preciso pinçar para confirmar. Arrastes de conteúdo/rolagem continuam disponíveis pelo modo clássico e gamepad; o novo arraste do indicador é exclusivo da moldura. A barra do teclado conserva reposicionamento por gamepad e pinça clássica. O caminho `updatePoke()` não foi reativado: assumia um plano sem rotação e concorreria com a seleção por raio. Não há segundo tracker nem alteração no MediaPipe/HandTracker.

A sensibilidade ajusta a tolerância a pequenas oscilações; o tempo de ativação pode variar de 300 a 1200 ms. Preferências são persistidas. As áreas dos cantos ficam fora do conteúdo e correspondem aos controles desenhados. A seleção considera profundidade, visibilidade, bloqueio e proprietário. A captura não depende de continuar sobre a barra/canto. A máquina de estados é IDLE → HOVER → ARMED → DRAGGING/RESIZING → RELEASED; cliques usam ARMED → RELEASED e exigem saída/movimento antes de rearmar.

Uma referência congelada no início do arraste impede realimentação pela própria movimentação da janela. Suavização, limites de velocidade/deslocamento e tamanho limitam saltos. Durante perda breve, a pose fica parada; recuperação refaz a âncora sem recuperar deslocamento perdido. Perda prolongada libera a captura. Pausa, mudança de modo, remoção, ocultação ou bloqueio também encerram a ação. Eventos de conteúdo não são enviados durante manipulação.

## Zoom da câmera

Na aba **Tela e mundo**, ative **Modo câmera** e use **Zoom da câmera**: −, +, slider, leitura atual e **Restaurar 1.0x**. O zoom digital varia de 1.0x a 2.0x e é persistido separadamente do navegador e do FOV/lentes. Sem passthrough ou em tela cheia os controles ficam desabilitados e o zoom efetivo é 1.0x; a preferência salva é preservada.

Foi escolhido recorte digital visual, sem solicitar zoom óptico nem alterar a sessão ARCore, CaptureRequest Camera2, imagem de inferência ou intrínsecos. O quad de câmera recebe escala em clip space; a mesma escala é aplicada às linhas X/Y da matriz de projeção, inclusive seu centro óptico. O valor é capturado uma vez por quadro para os dois olhos. Assim, a cena acompanha o enquadramento, preservando matematicamente o registro já existente. As telas e mãos aparentam maiores junto com a câmera; isso não muda o conteúdo/zoom das WebViews nem a calibração das lentes. Não se afirma calibração física nova nem ganho de resolução.

A escolha respeita a matriz de projeção orientada para o display fornecida pelo [ARCore Camera.getProjectionMatrix](https://developers.google.com/ar/reference/java/com/google/ar/core/Camera). No fallback Camera2 o recorte é posterior à rotação/ajuste de proporção já existentes; continua monocular, 3DoF e sem mãos.

## Testes e build desta alteração

Execução local em 19/11/2026, Java 17.0.21.1, Gradle 8.7, SDK 34/35 e NDK 27.0.12077973 existentes. Sem bootstrap e sem dependências novas.

- `:app:testDebugUnitTest :app:assembleDebug`: **BUILD SUCCESSFUL**, 2 min 41 s.
- **41 testes JUnit/Robolectric, 0 falhas, 0 erros, 0 ignorados**: AndroidInteractionTest (10), CameraZoomTest (3), FingertipGestureTest (11), InteractionPolicyTest (12), WorkspaceGeometryTest (5).
- Serviço Linux: **8 testes aprovados**, 5,085 s, incluindo TLS e pipeline JPEG sintético.
- APK: assinatura v2 válida, pacote `com.stormvr.workspace.mr`, minSdk 26/targetSdk 34, ARM 32/64 bits. Modelo no APK verificado com SHA-256 original.
- `git diff --check`: aprovado; `HandTracker.kt` e dependências não modificados.

APK: `Source/android/app/build/outputs/apk/debug/app-debug.apk` (99971500 bytes).
Cópia para instalar: `dist/fingertip-camera-zoom/StormVR-Workspace-MR-Fingertip-Zoom-Debug.apk`.
SHA-256: `9826b0113622f211fc13670397b001afc8d2dcefc898f687faa5446092d9690a`.
Logs/XML/HTML em `dist/fingertip-camera-zoom/`. Não confundir com a validação anterior em HAND_TRACKING_VALIDATION.md. O APK anterior em `dist/handtracking-debug/` foi preservado.

Os testes novos verificam ponta do indicador independente do polegar; hover/armed/captura/liberação; jitter; perda e recuperação; âncora sem salto, janela inclinada, limites e proporção; exclusão entre modos e gamepad; duas mãos e oclusão; persistência/restauração; controles do zoom e isolamento do WebView/FOV; transformação digital com matriz assimétrica e dois olhos. Os testes de UI usam Android simulado por Robolectric, sem executar shaders em GPU real.

## Verificação em aparelho ainda necessária

Nenhum aparelho foi encontrado por `adb devices`. Testar gesto de liberação e armar a captura com mãos diferentes, luz e distância; cruzamento de telas e duas mãos; telas curvas/rotacionadas; teclado/WebView/Moonlight; pausa e retomada. Testar zoom 1/1,5/2 em retrato/paisagem e nos dois olhos, ARCore/Camera2, com e sem lentes, verificando o alinhamento com a mão física e aquecimento. Os testes sintéticos e Robolectric validam lógica, geometria e preferências, não precisão/ergonomia nem shaders em GPU real.
