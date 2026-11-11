# API REST

Base: `/api`. JSON UTF-8. Valores monetários em BRL, com duas casas decimais no banco.

## Autorização e CSRF

O caixa usa HTTP Basic, somente via HTTPS em produção. O cliente consulta seus pedidos com `X-Order-Token`, recebido apenas na criação. Esse token não vai na URL.

Antes de POST ou PATCH, faça `GET /api/session` com cookies habilitados. A resposta contém `csrf`, `header` e `demo`. Reenvie o token no cabeçalho cujo nome está em `header`, mantendo os cookies da mesma sessão. A interface já faz isso. Ausência/erro de CSRF: 403. Acesso ao caixa sem autenticação: 401.

| Método | Rota                           | Acesso / resultado                               |
| ------ | ------------------------------ | ------------------------------------------------ |
| GET    | `/session`                     | Inicializa CSRF e informa modo demo              |
| GET    | `/menu`                        | Produtos disponíveis                             |
| GET    | `/tables/{token}`              | Número da mesa ativa                             |
| POST   | `/orders`                      | Cria pedido, 201, devolve pedido e token secreto |
| GET    | `/orders/{id}`                 | Cliente com `X-Order-Token`                      |
| POST   | `/orders/{id}/checkout`        | Abre ou reutiliza checkout                       |
| POST   | `/orders/{id}/sync`            | Consulta confirmação no provedor                 |
| POST   | `/orders/{id}/simulate`        | Simula confirmação apenas em modo demo           |
| GET    | `/cashier/orders/{id}/receipt` | Caixa, comprovante interno de pedido pago        |
| GET    | `/cashier/orders`              | Caixa, até 100 pedidos pagos mais recentes       |
| PATCH  | `/cashier/orders/{id}/status`  | Caixa, transição sequencial                      |
| GET    | `/cashier/tables`              | Caixa, mesas e links de QR                       |
| POST   | `/cashier/tables/{number}`     | Caixa, cadastra mesa de 1 a 999                  |
| GET    | `/cashier/tables/{number}/qr`  | Caixa, QR PNG 350 × 350                          |

### Criar pedido

```json
{
  "tableToken": "token-aleatorio-da-placa",
  "requestKey": "c08ad67f-0f63-456a-b3a8-40848f2a169b",
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 6, "quantity": 1 }
  ],
  "notes": "Uma pizza sem manjericão"
}
```

A chave `requestKey` deve ser um UUID novo por pedido. Reenvio com a mesma chave retorna 409; não cria outro pedido. Não envie preço ou total: campos desconhecidos são rejeitados. De 1 a 40 linhas, produtos sem repetição, de 1 a 20 unidades por linha e até 500 caracteres nas observações. Produtos inativos ou inexistentes retornam conflito.

Resposta contém `order` e `accessToken`. Guarde o token com segurança para acompanhar e pagar. O objeto `order` contém `id`, `tableNumber`, `status`, `total`, `notes`, `createdAt`, `paidAt`, `paymentId` e `items` (nome, quantidade e preço unitário). Antes da confirmação, data e identificador de pagamento são nulos.

### Atualizar preparo

```json
{ "status": "EM_PREPARO" }
```

Transições aceitas: `PAGO → EM_PREPARO → PRONTO → ENTREGUE`. O caixa não consegue marcar um pedido pendente como pago. O valor não pode ser modificado após a criação.

### Erros

Formato de negócio: `{"message":"Descrição do problema"}`. Erros de validação também incluem `errors`. Status principais: 400 (entrada inválida), 401/403 (acesso), 404 (mesa/pedido/token inválido), 409 (conflito), 502 (falha do provedor), 503 (pagamento sem configuração). Falhas de autenticação/CSRF são respondidas pelo Spring Security e podem não usar esse envelope.
