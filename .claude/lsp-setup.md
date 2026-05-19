# LSP setup для Claude Code

Sub-project D из Claude Code harness setup roadmap. Article reference:
Anthropic «How Claude Code works in large codebases» (May 2026) —
«For multi-language codebases, this is one of the highest-value
investments».

## Claude Code plugins (cached, ready to activate)

Уже installed в `~/.claude/plugins/cache/claude-plugins-official/`:

- `typescript-lsp` v1.0.0 — TypeScript/JavaScript LSP integration
- `jdtls-lsp` v1.0.0 — Java LSP integration (Eclipse JDT.LS)
- `pyright-lsp` v1.0.0 — Python LSP (не используется в проекте)

Plugins **auto-activate** когда Claude Code открывает соответствующий
файл (.ts/.tsx → typescript-lsp, .java → jdtls-lsp). Конфиг не нужен.

## Language server binaries (что должно быть в PATH)

### TypeScript: ✅ INSTALLED

```bash
npm install -g typescript-language-server typescript
```

Verify:
```bash
typescript-language-server --version  # expects 5.x.x
```

Текущая version: 5.2.0 (installed 2026-05-19).

После переустановки Node.js / nvm switch — может потребовать reinstall.
Команда выше идемпотентна (можно повторить).

### Java: ❌ PENDING (Eclipse mirrors blocked)

**Текущий статус:** не installed. Попытка manual install 2026-05-19
failed — Eclipse JDT.LS download mirrors возвращали 404 / corrupted
streams для:
- `https://download.eclipse.org/jdtls/snapshots/jdt-language-server-latest.tar.gz`
- `https://download.eclipse.org/jdtls/milestones/1.42.1/...`
- `https://download.eclipse.org/jdtls/releases/1.40.0/...`
- Maven Central + GitHub mirrors

**Когда mirrors заработают** — следующие шаги:

```bash
mkdir -p ~/.local/share/jdtls ~/.local/bin
cd /tmp

# Find working URL (попробовать несколько):
wget https://download.eclipse.org/jdtls/snapshots/jdt-language-server-latest.tar.gz -O jdtls.tar.gz

tar -xzf jdtls.tar.gz -C ~/.local/share/jdtls
```

Создать wrapper script `~/.local/bin/jdtls`:

```bash
#!/bin/bash
JDTLS_HOME="$HOME/.local/share/jdtls"
LAUNCHER=$(find "$JDTLS_HOME/plugins" -name "org.eclipse.equinox.launcher_*.jar" -type f | head -1)
CONFIG_DIR="$JDTLS_HOME/config_linux"
[ -d "$CONFIG_DIR" ] || CONFIG_DIR="$JDTLS_HOME/config"
WORKSPACE="${JDTLS_WORKSPACE:-$HOME/.cache/jdtls/workspace}"
mkdir -p "$WORKSPACE"

exec java \
  -Declipse.application=org.eclipse.jdt.ls.core.id1 \
  -Dosgi.bundles.defaultStartLevel=4 \
  -Declipse.product=org.eclipse.jdt.ls.core.product \
  -Xms1g -Xmx2g \
  --add-modules=ALL-SYSTEM \
  --add-opens java.base/java.util=ALL-UNNAMED \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  -jar "$LAUNCHER" \
  -configuration "$CONFIG_DIR" \
  -data "$WORKSPACE" \
  "$@"
```

```bash
chmod +x ~/.local/bin/jdtls
echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc
```

Alternative install methods (если mirrors всё ещё down):
- **Homebrew (если перехать на macOS):** `brew install jdtls`
- **Arch/Manjaro AUR:** `yay -S jdtls`
- **Manual mirror search:** check https://github.com/eclipse-jdtls/eclipse.jdt.ls releases (могут быть GitHub releases tarballs)

## Verification после полной установки

Перед verify обоих LSP serverов — restart Claude Code session (plugin
discovery runs at session start).

**TypeScript:**
1. Открыть любой `frontend/src/**/*.tsx` файл
2. Попросить Claude'а: «Покажи где функция X используется» → должно работать через LSP find references (symbol-level)
3. Попросить: «Перейди к определению Y» → goto-definition

**Java:**
1. Открыть любой `backend/src/main/java/**/*.java` файл
2. Аналогичные symbol nav requests
3. Verify через `tail -f ~/.cache/jdtls/workspace/.metadata/.log` для signs of jdtls activity

**Workspace state:**
- jdtls workspace cache: `~/.cache/jdtls/workspace/` (Eclipse JDT внутренние metadata, gitignored)
- LSP communication logs: `tail -f /tmp/claude-hooks-*.log` не покажет, LSP — separate channel (Claude Code internal)

## Value LSP даёт (after full setup)

| Capability | TypeScript | Java |
|---|---|---|
| Symbol go-to-definition | ✅ | ✅ |
| Find references workspace-wide | ✅ | ✅ |
| Hover info (types, JSDoc/Javadoc) | ✅ | ✅ |
| Real-time error detection | ✅ | ✅ |
| Safe rename refactor | ✅ | ✅ |

Per Anthropic article — LSP даёт «symbol-level precision» вместо
text-matching grep. Для multi-language codebase с 1000+ Java classes
+ ~100 TS components — высокий impact.

## Backlog

- ❌ Eclipse JDT.LS mirror access — try later when network unblocks
  OR manual file transfer от machine с work network
- Optional: Python pyright если в проекте появятся `.py` scripts
  (`npm install -g pyright`)
