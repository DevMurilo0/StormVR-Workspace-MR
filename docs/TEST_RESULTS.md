# Verificação — 2026-11-18

| Verificação | Resultado |
|---|---|
| Repositório oficial existente, branch feature/workspace-mr | Confirmado; dist Linux preexistente preservado |
| Ambiente preparado | Java 17, Gradle 8.7, wrapper completo, command-line tools 12 e Build Tools 34 no /home; plataformas/NDK/ADB incompletos por erros de rede |
| Build original | Não executado: toolchain ausente |
| assembleDebug modificado | Primeira tentativa: SDK location not found. Final, com Java 17 e caminho Linux: BUILD FAILED in 54s, CXX1101 por NDK incompleto/sem source.properties. Nenhum APK produzido |
| Cinco testes JUnit de geometria adicionados | Não executados: SDK/NDK completos continuam ausentes |
| Oito testes Python/Linux | Passaram: auth, metadados, JPEG, duas fontes distintas, expiração, página sem segredo e pipeline GStreamer real e TLS com certificado verificado |
| Python py_compile e bash -n | Passaram |
| Análise sintática Kotlin adicional | Novas classes, renderer, activity e state passaram; não substitui compilação. TvApps já apresenta limitação do parser na base original |
| git diff --check | Passou |
| KScreen inventário real | Um LVDS-1, 1366×768 ~60 Hz |
| Portal ScreenCast leitura de propriedades | v6; tipos 7; modos de cursor 7 |
| Captura real via portal | Não iniciada; depende de seleção interativa de fontes |
| FFmpeg VA-API H.264, 30 frames 720p | Código 0 no host; fallback i965 após falha inicial iHD |
| Drivers VA-API no Flatpak | iHD localizado; i965 não localizado na busca em /usr/lib |
| Inicialização Android/câmera/estéreo/rotação | Não testado; exige APK e celular |
| Moonlight pareamento/frames/reconexão/segundo plano | Código alterado; não testado em aparelho |
| Mãos, pinça, clique, arraste, duas mãos | Código integrado; não testado em aparelho |
| Layout salvar/restaurar | Código implementado; teste de integração pendente |
| Uma/duas/três telas em Android, memória/GL | Não testado |

Os testes HTTP usam fontes de teste, não capturas do desktop. O teste GStreamer usa videotestsrc e gera JPEG real. Eles não comprovam captura PipeWire, TLS em Android ou a experiência completa. Nenhum teste Android pendente deve ser considerado aprovado.

Reexecutar:

```bash
python -m unittest discover -s tools/workspace -p 'test_*.py' -v
python -m py_compile tools/workspace/*.py
bash -n Source/android/gradlew
# Após configurar toolchain:
Source/android/gradlew :app:testDebugUnitTest :app:assembleDebug
```

No celular, executar todos os cenários da solicitação: concessão/recusa de câmera; ARCore e Camera2; recenter; estéreo/FOV/IPD/distorção; segundo plano e perda de contexto; Wi-Fi desligado/ligado; frames crescentes durante mãos; adicionar/remover duas telas; fontes Linux diferentes; salvar/carregar; entradas alternativas e limites térmicos. Registrar modelo/API Android, codecs, logcat, FPS e memória. Não há dispositivo conectado validado nesta sessão.
