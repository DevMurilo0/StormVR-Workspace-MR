# Resultados — Spatial Hand Engine

Validação local em 19/11/2026. Base: `fdc8fee`. Branch: `improve/vr-hands-input`.

## Verificações realizadas

| Suíte | Testes | Falhas / erros |
|---|---:|---:|
| AndroidInteractionTest | 13 | 0 / 0 |
| CameraZoomTest | 3 | 0 / 0 |
| FingertipGestureTest | 11 | 0 / 0 |
| InteractionPolicyTest | 12 | 0 / 0 |
| SpatialGestureTest | 6 | 0 / 0 |
| SpatialHandEngineTest | 7 | 0 / 0 |
| SpatialRaycastTest | 4 | 0 / 0 |
| WorkspaceGeometryTest | 5 | 0 / 0 |

Total: **61 testes Android/JVM aprovados**, sem testes ignorados.

- Bridge Linux: `python -m unittest discover -s tools/workspace -p test_server.py` — **8 testes aprovados**.
- Modelo embarcado: `python tools/workspace/verify-hand-model.py` — tamanho e SHA-256 aprovados (7.819.105 bytes).
- Coletor de benchmark: sintaxe Python e execução de `--help` verificadas. Não foi executada coleta em dispositivo.
- `git diff --check` sem erros.

## Build

Comando executado a partir da raiz, usando a toolchain instalada e descoberta neste ambiente:

```sh
bash tools/workspace/build-android.sh :app:testDebugUnitTest :app:assembleDebug --offline --console=plain
```

Gradle 8.7, JDK 17, SDK Android 34 e NDK 27.0.12077973. Resultado final: **BUILD SUCCESSFUL**. O núcleo Moonlight foi compilado para `arm64-v8a` e `armeabi-v7a`. Há avisos existentes de APIs obsoletas/variáveis não usadas; não há erro de compilação.

Uma rodada intermediária falhou no teste novo que distingue movimento da cabeça de movimento da palma. A implementação final usa deslocamento da palma em coordenadas de cena e passou nessa regressão, inclusive congelamento e retomada da distância sem salto. A verificação final foi repetida após os últimos ajustes para assegurar que o APK corresponde ao código entregue.

APK debug (ignorado pelo Git, disponível no diretório de build):

`Source/android/app/build/outputs/apk/debug/app-debug.apk`

Tamanho: 100,264,315 bytes.

SHA-256: `fc9e52168be5faa26b57e094f35c42d6b1c9a5b8fca73537ab9efd8f19de2607`.

O APK contém `assets/hand_landmarker.task` e `libmoonlight-core.so` nas duas ABIs. Assinatura debug verificada com apksigner.

## Limites da evidência

`adb devices -l` não encontrou dispositivo conectado. Não foram realizados testes físicos de mãos, ARCore/Depth, Moonlight ao vivo, headset, Bluetooth ou consumo energético. Não há números medidos de precisão, estabilidade física, latência até o display, CPU, memória ou energia do Gesture Recognizer neste aparelho. O reconhecedor adicional foi pesquisado, sem substituição ou duplicação do modelo atual.

Os testes validam projeção sintética, rotação, rejeição de dados, estados e perda temporal, captura, profundidade relativa, sobreposição de telas, teclado/entrada Android e persistência de preferências. Eles não validam o modelo visual em câmera real. Confiança é heurística; orientação articular publicada é eixo do osso, sem estimar torção não observável. A medida de palma ainda é comum às duas mãos.

Os modos spatial/Raw Depth e os gestos novos continuam experimentais; o padrão anterior permanece disponível. Critérios de depth podem recusar toda a profundidade em determinados aparelhos. A compatibilidade só pode ser confirmada durante execução no dispositivo. Painel e controles foram compilados; estados de interação e persistência foram exercitados em testes. A legibilidade do painel não foi inspecionada em headset.

Consulte [SPATIAL_HAND_ENGINE.md](SPATIAL_HAND_ENGINE.md) para auditoria separando evidências e hipóteses, decisões de implementação, fontes primárias e protocolo de benchmark físico. Nenhum merge ou push foi realizado.
