# VendasAkes

Backend em Java 21 e Spring Boot para organizar ofertas de marketplaces. Esta primeira etapa contem o CRUD de ofertas, a regra inicial de duplicidade e apenas o contrato que futuros providers deverao implementar.

## Pre-requisitos

- Java 21
- PostgreSQL em execucao

Crie um banco vazio e defina as variaveis de ambiente no PowerShell antes de iniciar:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/vendasakes"
$env:DB_USERNAME = "seu_usuario"
$env:DB_PASSWORD = "sua_senha"
```

Depois execute:

```powershell
.\mvnw.cmd spring-boot:run
```

A API estara disponivel em `http://localhost:8080/api/ofertas`.

## Exemplo de cadastro

`POST /api/ofertas`

```json
{
  "marketplace": "MERCADO_LIVRE",
  "marketplaceProductId": "MLB123456789",
  "titulo": "Fone de ouvido Bluetooth",
  "precoOriginal": 249.90,
  "precoAtual": 189.90,
  "urlProduto": "https://produto.mercadolivre.com.br/MLB123456789",
  "urlAfiliado": null,
  "imagemUrl": "https://http2.mlstatic.com/exemplo.jpg"
}
```

O percentual de desconto, a data encontrada e o estado de publicacao sao definidos pelo service, e nao pelo cliente da API.

## Endpoints da etapa 1

| Metodo | Caminho | Acao |
|---|---|---|
| `GET` | `/api/ofertas` | Lista as ofertas |
| `GET` | `/api/ofertas/{id}` | Busca uma oferta |
| `POST` | `/api/ofertas` | Cadastra uma oferta |
| `PUT` | `/api/ofertas/{id}` | Atualiza uma oferta |
| `DELETE` | `/api/ofertas/{id}` | Exclui uma oferta |
| `POST` | `/api/ofertas/{id}/publicar` | Simula localmente a publicacao |

Na etapa 1, publicar apenas marca a oferta como publicada e registra a data. Nenhuma chamada ao Telegram ou ao Mercado Livre e realizada.
