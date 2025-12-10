# GameFluxer

Uma loja de jogos offline bonita para Android, construída com Kotlin e Material You.

## Características

- 🎮 Interface moderna com Material You / Material Design 3
- 📱 Suporte a múltiplas plataformas (Android, PC, etc.)
- 💾 Banco de dados totalmente offline
- 📦 Sistema de importação via arquivo ZIP
- 🔍 Pesquisa de jogos
- 📊 Tela de detalhes completa com screenshots
- ⚙️ Configurações para trocar de plataforma
- 🎨 Design inspirado no ApkBomb

## Como Usar

1. Compile o projeto no Android Studio
2. Instale o APK no seu dispositivo
3. Importe o arquivo `database_example.zip` através do botão FAB na tela principal
4. Navegue pelos jogos disponíveis

## Estrutura do Banco de Dados

O arquivo ZIP deve conter:
- `config.json` na raiz
- Arquivos JSON de jogos conforme especificado no config.json

### Exemplo de config.json:

```json
{
  "platforms": [
    {
      "name": "Android",
      "databasePath": "databases/android_games.json"
    },
    {
      "name": "PC",
      "databasePath": "databases/pc_games.json"
    }
  ]
}
```

### Exemplo de estrutura de jogo:

```json
{
  "id": "1",
  "name": "Nome do Jogo",
  "description": "Descrição completa",
  "version": "1.0",
  "size": "100 MB",
  "rating": 4.5,
  "developer": "Desenvolvedor",
  "category": "Categoria",
  "platform": "Android",
  "iconUrl": "URL do ícone",
  "bannerUrl": "URL do banner",
  "screenshots": "URL1, URL2, URL3",
  "downloadUrl": "URL de download",
  "releaseDate": "2024-01-01"
}
```

## Tecnologias Utilizadas

- Kotlin
- Jetpack Compose
- Material 3
- Room Database
- DataStore Preferences
- Coil (carregamento de imagens)
- Navigation Compose
- Coroutines & Flow

## Build

```bash
./gradlew assembleDebug
```

## Licença

Este projeto é de código aberto.
