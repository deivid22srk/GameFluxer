# Chaquopy Python for Android

https://chaquo.com/chaquopy/

## Bibliotecas Python Instaladas

- requests==2.32.5

## Localização dos Scripts

Os scripts Python estão em:
`app/src/main/python/`

## Como Adicionar Mais Bibliotecas

Edite `app/build.gradle.kts`:

```kotlin
python {
    pip {
        install("requests")
        install("beautifulsoup4")  // adicionar aqui
    }
}
```

## Documentação

https://chaquo.com/chaquopy/doc/current/android.html
