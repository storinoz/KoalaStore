# KoalaStore — By KoalaBoy

Aplicativo Android offline para **pequenos empreendedores e hobbistas que realizam vendas**. A ideia é organizar a venda de **quaisquer produtos que possam ser diferenciados por categorias**, com categorias e produtos definidos pelo próprio usuário.

O KoalaStore reúne clientes, produtos, orçamentos e compras de reposição em um aplicativo simples. Os dados ficam no dispositivo, sem conta online ou servidor.

## Baixar e instalar

**[Baixar KoalaStore 0.10.0 — APK](https://github.com/storinoz/KoalaStore/releases/download/v0.10.0/KoalaStore-0.10.0.apk)** · [Todas as versões públicas](https://github.com/storinoz/KoalaStore/releases)

Requer **Android 8.0 ou superior**. Abra o APK no celular e permita a instalação pelo navegador ou gerenciador de arquivos utilizado, se solicitado.

Se já tiver uma versão oficial instalada, escolha **Atualizar**, sem desinstalar a anterior. As atualizações oficiais preservam os cadastros existentes. Desinstalar o aplicativo ou limpar seus dados apaga os registros locais.

## Para quem é

Para quem vende em pequena escala e precisa organizar contatos, preços, propostas e reposições: pequenos empreendedores, vendedores independentes e hobbistas. As categorias são livres, permitindo adaptar o aplicativo aos produtos de cada atividade.

## Funcionalidades

- **Clientes:** nome, telefone e atalho para WhatsApp.
- **Categorias e produtos:** categorias personalizadas, preço unitário de venda, busca e filtro por categoria. Estoque opcional, controlado manualmente.
- **Orçamentos:** vários produtos, quantidades, datas de venda e pagamento, desconto ou taxa em reais ou percentual, status de pagamento/entrega e compartilhamento por texto ou imagem no WhatsApp.
- **Filtros e ordenação:** mês da venda, status, pesquisa, data, valor e cliente.
- **Compras de reposição:** empresa, datas, produtos já cadastrados, quantidades, preços de aquisição, frete e outras taxas.
- **Custos e preços:** custo médio com frete/taxas rateados, margem por produto e preço de venda recomendado. Após uma compra nova ou alterada, é possível revisar os preços; a mudança só é gravada ao salvar essa revisão.
- **Aparência:** temas claro, escuro e automático; escuro por padrão.

O preço recomendado usa a margem sobre a venda: `custo / (1 - margem)`. Por exemplo, custo de R$ 6,00 e margem de 40% resultam em preço recomendado de R$ 10,00. O preço cadastrado permanece sob controle do usuário.

## Dados e privacidade

**Toda instalação nova começa vazia:** sem clientes, categorias, produtos, compras ou orçamentos pré-cadastrados. Nenhum catálogo pessoal ou banco de dados acompanha o APK ou o código público.

Os registros ficam no banco privado do aplicativo no celular. O KoalaStore não solicita permissão de internet e não oferece sincronização ou backup automático em nuvem. Ainda não há backup manual pelo aplicativo.

O WhatsApp é aberto externamente; o usuário revisa o destinatário e confirma o envio nele. O compartilhamento de imagens pode solicitar a seleção do contato. O funcionamento do WhatsApp depende da instalação e da conexão desse aplicativo.

A importação opcional em Configurações aceita um catálogo JSON local, escolhido pelo usuário, sem substituir preços ou estoque de produtos equivalentes. O formato contém `catalogId` e uma lista `products` com `sourceId`, `name`, `category` e `priceCents` em centavos. Nenhum catálogo é fornecido neste repositório.

## Desenvolvimento

Projeto em **Kotlin, Jetpack Compose e Room/SQLite**. Use JDK 17, SDK Android 36 e Build Tools 35.0.0. Configure o SDK no Android Studio, em `local.properties` ou pela variável `ANDROID_HOME`.

A chave das versões oficiais não é distribuída. Para compilar uma cópia de desenvolvimento no Windows, gere uma chave local própria com os parâmetros esperados pela configuração atual:

```powershell
New-Item -ItemType Directory -Force signing
keytool -genkeypair -keystore signing/koalastore-legacy.keystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=KoalaStore local"
.\gradlew.bat assembleRelease testDebugUnitTest lintRelease
```

APKs assinados com outra chave não atualizam a instalação oficial. A chave gerada acima destina-se à cópia local de desenvolvimento. Nunca envie chaves, bancos ou catálogos pessoais ao GitHub.

O APK é gerado em `app/build/outputs/apk/release/app-release.apk`. Para manter saídas fora da pasta do projeto, defina `KOALASTORE_BUILD_DIR` e use a opção Gradle `--project-cache-dir` com diretórios externos.

Os testes usam dados fictícios isolados, que não são carregados no aplicativo distribuído. A versão 0.10.0 passou em **53 testes automatizados**, com lint sem erros. Não houve validação em aparelho físico nesta entrega.

## Sobre esta publicação

O histórico público começa com uma cópia limpa da versão 0.10.0. Os artefatos pessoais e o histórico anterior foram mantidos fora deste repositório público. O APK oficial conserva o identificador e a assinatura das versões anteriores para permitir atualizações compatíveis.

Novas versões do aplicativo e publicações no GitHub são feitas conforme solicitação do mantenedor. A distribuição atual é pelo GitHub; uma eventual publicação na Google Play será preparada separadamente.
