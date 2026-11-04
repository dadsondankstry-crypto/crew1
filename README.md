# Minha Escala 1.0 — Android nativo, OCR offline

Aplicativo **independente** do projeto Explorador ZIP. Importa fotografias de tabelas de escala de trabalho e avisos de treinamento, reconhece texto **no próprio aparelho**, pesquisa nomes e mostra possíveis horários organizados em cartões. O usuário pode confirmar/corrigir informações e salvar favoritos.

## O que funciona no código

- Seleção de **uma ou várias fotos** pelo seletor de arquivos do Android, sem solicitar acesso geral à galeria.
- Cópia das fotos selecionadas para o armazenamento privado do aplicativo.
- OCR latino local com **ML Kit Text Recognition v2 (modelo bundled)**.
- Pesquisa por nome ou sobrenome, ignorando acentos e diferenças de maiúsculas/minúsculas.
- Associação aproximada entre uma linha que contém o nome e números de horário na mesma altura.
- Tentativa conservadora de identificar data no cabeçalho, com correção manual.
- Separação de fotos classificadas como **TREINAMENTO** e **ESCALA**.
- Exibição da foto original, texto OCR completo e uma lista de linhas reorganizadas.
- Correção manual do texto, cadastro e edição de horário confirmado.
- Pesquisa restrita à data de hoje, quando a data está presente e foi identificada.
- Favoritos pesquisáveis, guardados localmente.
- Exportação de resultados de busca em CSV sob confirmação.
- Exclusão seletiva de cópias de fotos, textos e todos os dados privados do aplicativo.
- Projeto com ícones tradicionais e adaptativos, código Java e workflow GitHub Actions.

## O que NÃO é garantido nesta primeira versão

- Leitura perfeita de tabelas densas, tortas, sobrepostas ou com letras pequenas.
- Detecção automática infalível de qual horário corresponde a cada trabalhador.
- Reconstrução fiel de todas as células da tabela; a visão reorganizada é uma **estimativa**.
- Datas em fotos de treinamento com tabela de embarque: o campo data fica em branco para evitar confundir **DATE EMBARK** com dia do treinamento.
- Alertas/notificações, captura pela câmera, busca por pessoas sem fotos importadas, integração com sistemas da empresa.
- Compilação e teste em um Android real no ambiente em que o ZIP foi preparado.

## Escolha tecnológica

**Java nativo + ML Kit bundled 16.0.1**. Uma PWA poderia importar fotos, mas OCR offline com modelo embutido é mais direto e previsível neste APK. O código utiliza Storage Access Framework / ACTION_OPEN_DOCUMENT sem pedir permissões amplas de armazenamento. `INTERNET` não é declarada no Manifest: as fotos não são enviadas a servidores.

## Primeira instalação: ZiptoGit e GitHub Actions

1. Criar um repositório GitHub **separado**, por exemplo `MinhaEscalaAndroid` (preferencialmente privado).
2. Enviar **o ZIP original** por ZiptoGit, sem descompactar. O ZIP tem os arquivos diretamente na raiz, inclusive `.github/workflows/main.yml`.
3. GitHub > repositório > **Actions** > `Gerar APK Minha Escala`.
4. Depois do build verde, abrir **Artifacts** > `MinhaEscala-APK` e baixar.
5. Extrair o artefato baixado e instalar `app-debug.apk` no Android.
6. No aplicativo: **Importar fotos** > buscar nome > **Ver foto** > **Corrigir / confirmar** para salvar um horário conferido.

Se a pasta `.github` não for publicada, crie manualmente `.github/workflows/main.yml` copiando `YAML_PARA_COPIAR.yml` (backup incluído). **A compilação no GitHub exige internet**; o OCR dentro do APK já instalado não.

## Privacidade dos colegas

As fotos de escalas podem conter **nomes completos e IDs de funcionários**. Não foram incluídas neste projeto as fotos pessoais enviadas na conversa. Na primeira execução, o aplicativo está vazio e você decide quais fotos importar. Não envie a galeria ou CSV com nomes para repositórios públicos; compartilhe relatórios somente com autorização. Dados locais são apagados ao limpar dados/desinstalar o aplicativo (não há backup automático do aplicativo).

## Testes sugeridos

1. Importar uma foto da tabela diária e outra do aviso de treinamento.
2. Pesquisar um sobrenome visível nas duas fotos.
3. Conferir se o aviso aparece como **TREINAMENTO**, não como turno.
4. Corrigir a data da foto caso necessário.
5. Confirmar manualmente um horário e conferir o filtro **Só hoje**.
6. Fechar e reabrir o app para verificar a persistência dos registros.
7. Adicionar um favorito e testá-lo sem digitar novamente.

## Versões e atualizações

`applicationId = com.exemplo.minhaescala`, versão `1.0.0`, `versionCode = 1`. Para atualizar sem perder dados, preserve o identificador, aumente `versionCode` e utilize assinatura compatível; APKs `debug` de diferentes ambientes podem ter certificados diferentes.

### Dependências de build

- JDK 17, Gradle 8.11.1, Android Gradle Plugin 8.9.2, compile/target SDK 35, min SDK 23.
- Google ML Kit `com.google.mlkit:text-recognition:16.0.1` (modelo latino embutido na compilação).

Este é um **projeto-fonte**, não um APK já compilado. Use os logs do GitHub Actions caso haja erro; não esconda falhas de compilação nem trate sugestões OCR como dados oficiais.
