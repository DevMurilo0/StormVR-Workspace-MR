# APK debug com mãos e teclado VR

Pacote: `com.stormvr.workspace.mr`. Não substitui o StormVR original (`com.stormvr.app`). Atualizar uma instalação anterior do Workspace MR exige a mesma chave debug; não desinstale sem salvar seus dados.

## Download pelo GitHub Actions

1. Envie esta branch ao seu repositório GitHub, incluindo `.github/workflows/handtracking-debug.yml` e o modelo em assets.
2. Abra **Actions → StormVR HandTracking Debug APK → Run workflow** e escolha a branch.
3. Aguarde todas as etapas verdes. Falhas de teste, compilação, modelo ou APK ausente interrompem o job.
4. Na execução concluída, em **Artifacts**, baixe **StormVR-Workspace-MR-HandTracking-Debug**. É necessário entrar no GitHub.
5. Extraia o ZIP. Ele contém o APK, SHA-256 e relatórios JUnit. Artifacts expiram em 30 dias.

O workflow usa Java 17, Gradle 8.7, SDK 34/35 e NDK 27.0.12077973. Só tem `contents: read`, usa assinatura debug e não publica Releases nem usa segredos. Não há execução remota presumida: criar o arquivo local não significa que um workflow foi disparado no GitHub.

## Build local

Na raiz do repositório, confira RAM, disco e toolchain antes do bootstrap (mínimo documentado: 10 GiB livres). O bootstrap instala dependências em diretório próprio, sem sudo:

```bash
python tools/workspace/bootstrap-android.py --accept-licenses
python tools/workspace/verify-hand-model.py
bash tools/workspace/build-android.sh :app:testDebugUnitTest :app:assembleDebug
python -m unittest discover -s tools/workspace -p 'test_*.py' -v
sha256sum Source/android/app/build/outputs/apk/debug/app-debug.apk
```

Use `STORMVR_TOOLCHAIN` para selecionar outra toolchain. O script existente carrega seu `environment.sh`. Robolectric só é dependência de teste, não entra no APK. Seus testes usam Android simulado, sem câmera ou aparelho.

Saída: `Source/android/app/build/outputs/apk/debug/app-debug.apk`.

## Instalar

Transfira o APK para o Android, autorize temporariamente **Instalar apps desconhecidos** no aplicativo que abrirá o arquivo e toque em instalar. Ou, com depuração USB autorizada:

```bash
adb install -r Source/android/app/build/outputs/apk/debug/app-debug.apk
```

Permita câmera ao abrir o app. ARCore compatível é necessário para mãos; o fallback Camera2 tem passthrough/3DoF e **não rastreia mãos**. O modelo de 7.819.105 bytes está no APK, validado por SHA-256; não requer download no primeiro uso.

## Uso

Abra as preferências do Workspace para **Interação VR**. Os padrões usam pinça com histerese e debounce, mão direita, teclado a 0,75 m e escala 1,15. Abra a mão antes da primeira pinça e depois de perder o rastreamento. Aponte, pince e solte; perda de rastreamento cancela o pressionamento. O conteúdo recebe DOWN/MOVE/UP; a tecla virtual só digita ao soltar. Backspace segurado repete após 500 ms a cada 100 ms. Clique por permanência usa 0,8–1,2 s, progresso circular e tolerância espacial.

O teclado oferece ABC/123/Símb./Avançado, ç e acentos, Caps/Shift, setas, Enter e Fechar. A barra inferior permite reposicionar por órbita. Tamanho e distância ficam nas preferências. Selecione um campo na tela de destino antes de digitar. O teclado preserva o foco desse campo. A ponte Linux JPEG/HTTPS continua sem injeção remota de mouse/teclado; eventos locais numa página WebView não adicionam essa capacidade.

## Validação física obrigatória

Testar aparelho/API, orientação retrato/paisagem, ambos os olhos, cruzamento/oclusão de mãos, iluminação, aquecimento por 20 minutos, recuperação de pausa, links/campos/rolagem/arraste em WebViews, sobreposição de telas, IME de sites externos e sessão Moonlight. JUnit/Robolectric não medem precisão, latência real nem ergonomia VR. Veja `HAND_TRACKING_VALIDATION.md` para resultados desta alteração.
