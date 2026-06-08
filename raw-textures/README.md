# Texturas cruas (raw)

Salve aqui as imagens originais (alta resolução, geradas por IA) com o **nome do item**.
Eu processo (removo fundo + redimensiono) e gero a textura final em
`resourcepack/assets/deadzone/textures/item/<id>.png`.

Exemplos de nomes esperados:
- `gas_mask.png`
- `bandagem.png`
- `kit_primeiros_socorros.png`
- ...

Processamento manual (se quiser rodar você mesmo):
```
java tools/TextureProcess.java raw-textures/gas_mask.png resourcepack/assets/deadzone/textures/item/gas_mask.png 64
```
