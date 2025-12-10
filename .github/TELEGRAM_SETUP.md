# 🤖 Configuração do Telegram Bot para GitHub Actions

## ⚠️ IMPORTANTE - Configure os Secrets PRIMEIRO

O workflow **falhará** se você não configurar os secrets do Telegram. Siga os passos abaixo:

## 🔐 Passo 1: Adicionar Secrets no GitHub

### 1. Acesse as configurações do repositório:
```
https://github.com/deivid22srk/GameFluxer/settings/secrets/actions
```

### 2. Clique em **"New repository secret"**

### 3. Adicione o primeiro secret:
- **Name:** `TELEGRAM_BOT_TOKEN`
- **Value:** `8223002882:AAHc4n7whOcjw-BQhKry1X20Aqedxj9nGvM`
- Clique em **"Add secret"**

### 4. Adicione o segundo secret:
- **Name:** `TELEGRAM_CHAT_ID`  
- **Value:** `-1003414093423`
- Clique em **"Add secret"**

## ✅ Verificando se está correto

Após adicionar, você deve ver 2 secrets na lista:
- 🔒 TELEGRAM_BOT_TOKEN
- 🔒 TELEGRAM_CHAT_ID

## 🚀 Como Funciona

A cada build bem-sucedido, o workflow:

1. ✅ Compila o APK Debug
2. 📦 Compacta o código fonte (sem build/, .gradle/, .git/)
3. 📤 Envia o ZIP para o grupo do Telegram com informações:
   - Hash do commit
   - Mensagem do commit
   - Autor
   - Data e hora
   - Branch

## 📑 Mensagem no Telegram

```
🚀 GameFluxer - Código Fonte

✅ Build: Sucesso
📦 Commit: `abc1234`
💬 Mensagem: Sua mensagem de commit
👤 Autor: deivid22srk
📅 Data: 2025-12-05 14:30
🌿 Branch: `main`

📎 Arquivo: GameFluxer-source-abc1234.zip
```

## 🧪 Testando

### Opção 1 - Fazer um commit:
```bash
git add .
git commit -m "Teste do workflow"
git push origin main
```

### Opção 2 - Executar manualmente:
1. Acesse: `https://github.com/deivid22srk/GameFluxer/actions`
2. Clique em **"Android CI"**
3. Clique em **"Run workflow"**
4. Selecione a branch `main`
5. Clique em **"Run workflow"**

## ❌ Se ainda der erro "Not Found"

Verifique:
1. ✅ Os secrets foram adicionados corretamente (sem espaços extras)
2. ✅ O bot foi adicionado ao grupo `-1003414093423`
3. ✅ O bot tem permissão para enviar mensagens no grupo
