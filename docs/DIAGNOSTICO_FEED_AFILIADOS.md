# Diagnóstico isolado do feed de afiliados

## Escopo

Investigar Network/Fetch/XHR do hub informado pelo usuário:
`https://www.mercadolivre.com.br/afiliados/hub?is_affiliate=true#menu-user`.

O programa `br.com.akesofertas.diagnostico.DiagnosticoOfertasNetwork` não é um bean
Spring. Reutiliza apenas o lançador de navegador e o perfil `.local/mercadolivre`.
Não chama services de negócio, API OAuth, Telegram ou banco; não substitui a busca
oficial existente. Não existe coletor definitivo ou repetição de requisições HTTP.

Arquitetura a avaliar após a captura: observador/coletor do portal separado da
busca oficial; ambos poderiam futuramente entregar ofertas normalizadas ao mesmo
fluxo. A geração privada continuaria atrás de `AffiliateLinkClient`. Nenhuma dessas
ligações novas foi implementada nesta investigação.

## Executar

```powershell
.\diagnosticar-ofertas.ps1
```

Padrão: dez minutos. Para três minutos:

```powershell
.\diagnosticar-ofertas.ps1 -Segundos 180
```

O programa abre Chrome instalado (ou msedge via AFILIADOS_NAVEGADOR), com o perfil
privado fixo. Não define senha, exporta sessão ou usa o perfil pessoal do navegador.
Feche outra instância que esteja usando esse mesmo perfil; não apague locks de um
navegador em execução. O diagnóstico não precisa reiniciar o Spring ou o ngrok.

1. Faça login manualmente na janela se necessário. OAuth do backend é outra sessão.
2. Abra a seção de ofertas do hub e observe o carregamento inicial.
3. Role a lista ou use Próxima página manualmente, para observar a paginação.
4. Opcionalmente, abra o Gerador de Links na mesma janela/perfil e gere links
   manualmente. Se a interface aceitar várias URLs, comece com duas; não envie mais
   de 20 por requisição nesta investigação. Não é feito POST adicional pelo programa.
5. Feche a janela ou aguarde o prazo. O relatório fica em
   `.local/diagnosticos/ofertas-<instante>.jsonl`.

O relatório é privado e ignorado pelo Git. Não compartilhe o perfil, HAR, trace ou
“Copy as cURL”: esses formatos podem conter credenciais. O programa não os produz.

## Informações registradas

| Informação | Representação no relatório |
| --- | --- |
| Endpoint e método | HTTPS, host/caminho sanitizados, método e status HTTP |
| Query | Somente nomes reconhecidos, sem valores |
| Body e resposta | Estrutura JSON, tipos e campos conhecidos |
| item_id / product_id / catalog_product_id | Caminhos e amostras de IDs MLB válidos |
| URL normal do produto | Campo/tipo e referência derivada do ID presente na URL, quando reconhecida |
| Preço atual/anterior/desconto | Campos/tipos; valores numéricos diretos nos objetos com IDs reconhecidos |
| Cupom | Presença, caminho e estrutura; código não é gravado |
| Paginação | Campos numéricos/booleanos conhecidos; cursor/URL seguinte apenas como tipo |
| Quantidade | Tamanho observado de cada array, com seu caminho |
| Lote createLink | Contagens de entrada/saída e resultado individual sanitizado |

O sanitizador é `ResumoNetworkSeguro`. Strings arbitrárias, nomes desconhecidos,
subdomínios desconhecidos e segmentos de caminho fora da lista de rotas reconhecidas
são omitidos. Isso inclui segredos inseridos em nomes de chaves e caminhos. Um endpoint
com `{segmento_omitido}` está incompleto: não deve ser copiado para um cliente HTTP.
É necessário conferir apenas o segmento de rota no DevTools, sem copiar query/credenciais,
antes de eventualmente ampliar a lista permitida. Não presumir contrato com base nisso.

URLs de produto no relatório são referências **derivadas**, não a URL original
completa nem links prontos para publicação. O endereço original pode conter tracking
ou sessão. Não são armazenados valores de cookies/headers de autenticação; eles nem
são solicitados ao Playwright. Bodies JSON são processados em memória e projetados
antes da gravação. Corpos HTML/texto são omitidos.

Limites: até 100 respostas por execução, JSON de até 2 MB, profundidade 10, 2.000 nós,
80 campos por objeto e 20 elementos por array. A contagem do array permanece a real,
mesmo quando a amostra é limitada. Tamanho de um array não prova que ele contenha
produtos: é preciso verificar o caminho e os campos. Preços e descontos não são
calculados nem interpretados como oferta válida por este diagnóstico.

## Geração em lote

Contrato informado pelo usuário, ainda privado:

```http
POST https://www.mercadolivre.com.br/affiliate-program/api/v2/affiliates/createLink
```

```json
{"urls":["URL_1","URL_2"],"tag":"telegram"}
```

O observador registra `quantidadeEnviada`, `quantidadeRecebida`, `created`, se o
`short_url` tem formato HTTPS meli.la e os índices de entrada correspondentes ao
`origin_url` devolvido. Não associa resultados apenas pela posição: podem existir
reordenação, falha parcial, ausência ou duplicidade. URLs e links curtos não são
copiados para esse resumo. Capturas acima de 20 URLs não têm análise individual de lote.

Aceitar um array não comprova que 20 URLs funcionem, nem que todos os resultados
tenham sucesso. Vinte é nosso teto de investigação, não um limite oficial confirmado.
É necessário observar uma chamada real com duas ou mais URLs e conferir a resposta.
Se a interface enviar chamadas individuais, isso não confirma suporte de lote. Não
forçamos uma chamada privada para compensar essa ausência.

## Login e segurança

Login é manual e suas páginas/rotas não são registradas. A captura para ao detectar
401/403/429 em uma chamada elegível ou um desafio visível/URL de desafio. Não há
tentativas, cliques de resolução, stealth ou repetição automática. A janela fica
disponível até o prazo para acesso manual; depois da pausa é preciso reiniciar o
diagnóstico para voltar a capturar. A detecção é conservadora e não cobre todo desafio
possível: se aparecer outro pedido de segurança, encerre o diagnóstico e faça o
acesso manualmente. Não é implementada geração automática para ultrapassar o desafio.

## Limitações da observação

Observa Fetch/XHR de domínios Mercado Livre, em abas do hub, do gerador e de /ofertas.
Rotas de autenticação, conta e telemetria ficam fora. Não desativa proteções nem
service workers. Falhas antes de receber resposta, WebSockets, documentos HTML,
dados incorporados no HTML, cache e outros caminhos podem ficar fora da captura.
Nenhuma resposta registrada não significa que a página não possua produtos ou API.

Base técnica: [eventos de rede do Playwright](https://playwright.dev/java/docs/network)
e [requestfinished](https://playwright.dev/java/docs/api/class-request), que permite
analisar a resposta depois do download. São usados observadores, não interceptação
ou alteração de requests.

## Estado da investigação

- Ferramenta implementada e testes simulados de privacidade/lote concluídos.
- Primeira execução real abriu o hub em 05/09/2026 e foi direcionada ao login manual.
- A execução terminou sem respostas de feed registradas. O usuário informou limite
  de tentativas e recusa do login Google por navegador não confiável. Não foram
  realizadas novas tentativas ou mudanças para disfarçar a automação.
- Endpoint do feed, paginação e contrato real de ofertas ainda dependem da captura
  autenticada. Não foram inferidos a partir de endpoints antigos ou de dados simulados.
- Sucesso de createLink em lote ainda não confirmado por captura local.

Só depois da captura será possível definir o adaptador definitivo e seus campos.

### Bloqueio de acesso observado

O [Google documenta que pode recusar login em navegadores controlados por automação](https://support.google.com/accounts/answer/7675428?co=GENIE.Platform%3DDesktop&hl=en).
Portanto essa recusa não indica defeito no OAuth Spring e não deve ser tratada com
flags de disfarce, extração de cookies ou novas tentativas automáticas.

Enquanto o perfil de diagnóstico não tiver acesso permitido, a alternativa para a
investigação é observar **manualmente** o DevTools de um navegador normal já autenticado:
Network → Fetch/XHR, abrir ofertas e identificar a chamada que entrega os produtos.
Anote endpoint sem valores de query, método, nomes de campos, contagens e paginação.
Não exporte cookies, HAR ou cURL bruto. Isso é inspeção manual do contrato; não conecta
o perfil pessoal à automação e não implementa coletor definitivo. Caso o acesso normal
também esteja bloqueado, siga as orientações de recuperação/suporte do próprio site.
