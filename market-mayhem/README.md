# Market Mayhem - Real-time Multiplayer Trading Game

Market Mayhem is a real-time, multiplayer Java trading game that doubles as a showcase for trading platform technologies. Players compete in timed sessions by placing BUY/SELL orders to maximize their P&L using a complete order matching engine with risk management.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Market Mayhem                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐    WebSocket/STOMP     ┌─────────────────────┐ │
│  │   JavaFX    │◄─────────────────────►│   Spring Boot       │ │
│  │   Client    │                        │   Server            │ │
│  │             │                        │                     │ │
│  │ • Order UI  │                        │ ┌─────────────────┐ │ │
│  │ • Book View │                        │ │ Matching Engine │ │ │
│  │ • P&L Track │                        │ │ • Order Book    │ │ │
│  └─────────────┘                        │ │ • FIFO Matching │ │ │
│                                          │ │ • Risk Rules    │ │ │
│                                          │ └─────────────────┘ │ │
│                                          │                     │ │
│                                          │ ┌─────────────────┐ │ │
│                                          │ │ Market Data     │ │ │
│                                          │ │ • Tick Generator│ │ │
│                                          │ │ • Price Feeds   │ │ │
│                                          │ └─────────────────┘ │ │
│                                          │                     │ │
│                                          │ ┌─────────────────┐ │ │
│                                          │ │ Persistence     │ │ │
│                                          │ │ • Orders/Trades │ │ │
│  ┌─────────────┐     PostgreSQL         │ │ • Player Scores │ │ │
│  │ PostgreSQL  │◄─────────────────────── │ │ • Risk Tracking │ │ │
│  │ Database    │                        │ └─────────────────┘ │ │
│  └─────────────┘                        └─────────────────────┘ │
│                                                                 │
│  Optional:                                                      │
│  ┌─────────────┐     Kafka Topics                              │
│  │   Kafka     │◄─────────────────────────────────────────────┤ │
│  │             │   • trades.out                               │ │
│  │             │   • marketdata.in                            │ │
│  └─────────────┘                                              │ │
└─────────────────────────────────────────────────────────────────┘
```

## Features

### Core Trading Engine
- **Real-time Order Matching**: Price/time priority with FIFO execution
- **Order Types**: LIMIT and MARKET orders
- **Partial Fills**: Support for partial order execution
- **Order Book**: Live top-10 levels via WebSocket
- **Trade Recording**: Complete audit trail with timestamps

### Risk Management
- **Order Size Limits**: Configurable maximum order size (default: 50,000)
- **Restricted Symbols**: Blacklist symbols (default: GME)
- **Violation Tracking**: Player risk violation scores

### Game Mechanics
- **Timed Sessions**: 8-minute trading sessions (configurable)
- **P&L Scoring**: Realized profit/loss tracking
- **Leaderboard**: Rankings by P&L and violation count
- **Multiple Symbols**: AAPL and MSFT (configurable)

### Real-time Features
- **WebSocket/STOMP**: Live order book updates, trades, market data
- **Market Data**: Synthetic tick generation (250ms intervals)
- **Live Scoring**: Real-time P&L updates

### Technical Integrations
- **IOI XML**: JAXB-based Indication of Interest import/export
- **REST API**: Leaderboard and IOI endpoints
- **Optional Kafka**: Event streaming capability

## Tech Stack

- **Java 17** - Modern LTS Java
- **Spring Boot 3** - Web, WebSocket, JPA, Validation, Actuator
- **PostgreSQL 16** - Primary persistence
- **JavaFX** - Desktop client UI
- **JAXB (Jakarta)** - XML processing
- **Maven** - Build and dependency management
- **Docker Compose** - Local development infrastructure
- **JUnit 5 + Mockito** - Testing framework
- **Testcontainers** - Integration testing

## Quick Start

### Prerequisites
- Java 17+
- Docker and Docker Compose
- Maven (or use included wrapper)

### 1. Start Infrastructure

```bash
docker compose up -d
```

This starts PostgreSQL on port 5432 with database/user/password: `trading/trading/trading`.

### 2. Build and Run Server

```bash
./mvnw clean package
./mvnw spring-boot:run
```

Server starts on `http://localhost:8080`

### 3. Run JavaFX Client

```bash
./mvnw -Pclient javafx:run
```

### 4. Verify Setup

**Health Check:**
```bash
curl http://localhost:8080/actuator/health
```

**Sample Trade Flow:**
1. Start JavaFX client
2. Connect with Player ID: "Player1"  
3. Place a SELL order: AAPL, 100 shares @ $150.00
4. Place a BUY order: AAPL, 50 shares @ $150.00
5. Watch the trade execute and P&L update

## API Reference

### WebSocket Endpoints

**Connection:** `ws://localhost:8080/ws`

**Subscriptions:**
- `/topic/room/{roomId}/book/{symbol}` - Order book updates
- `/topic/room/{roomId}/trades` - Trade executions
- `/topic/room/{roomId}/ticks` - Market data ticks
- `/topic/room/{roomId}/score` - Score updates

**Send Messages:**
- `/app/room/{roomId}/order.place` - Place order
- `/app/room/{roomId}/order.cancel` - Cancel order

### Sample Payloads

**Place Limit Order:**
```json
{
  "player": "Player1",
  "clOrdId": "ORDER_123",
  "symbol": "AAPL",
  "side": "BUY",
  "qty": 100,
  "type": "LIMIT",
  "price": 189.50
}
```

**Place Market Order:**
```json
{
  "player": "Player1", 
  "clOrdId": "MARKET_456",
  "symbol": "MSFT",
  "side": "SELL", 
  "qty": 50,
  "type": "MARKET"
}
```

**Cancel Order:**
```json
{
  "player": "Player1",
  "clOrdId": "ORDER_123"
}
```

### REST API

**Leaderboard:**
```bash
curl http://localhost:8080/api/leaderboard
```

**Store IOI (XML):**
```bash
curl -X POST http://localhost:8080/api/ioi/xml \
  -H "Content-Type: application/xml" \
  -d '<IOI><ioiId>IOI_001</ioiId><symbol>AAPL</symbol>...</IOI>'
```

**Get IOI:**
```bash
curl http://localhost:8080/api/ioi?symbol=AAPL
```

## Game Rules

### Session Flow
1. **Join Room**: Players connect to `/room/{id}` 
2. **Trading Session**: 8-minute timer (configurable)
3. **Order Placement**: LIMIT/MARKET orders via WebSocket
4. **Matching**: Real-time order book matching
5. **Scoring**: P&L tracking with violation penalties
6. **Victory**: Highest P&L with lowest violations

### Order Matching Rules
- **Price Priority**: Best price gets priority
- **Time Priority**: FIFO within same price level
- **Crossing Logic**: 
  - BUY crosses when price >= best ask
  - SELL crosses when price <= best bid
- **Market Orders**: Execute immediately at best opposing price
- **Partial Fills**: Orders can be partially executed

### Risk Controls
- **Max Order Size**: 50,000 shares (configurable)
- **Restricted Symbols**: GME blocked by default
- **Violation Scoring**: Risk violations count against player

## Configuration

Key settings in `application.yml`:

```yaml
game:
  session:
    duration-minutes: 8
  symbols:
    - AAPL
    - MSFT
  market-data:
    tick-interval-ms: 250
  risk:
    max-order-size: 50000
    restricted-symbols:
      - GME
```

## Testing

**Run All Tests:**
```bash
./mvnw test
```

**Integration Tests Only:**
```bash
./mvnw failsafe:integration-test
```

**Performance Test:**
The integration tests include a basic performance benchmark targeting ~1,000 orders/second locally.

## Optional: Kafka Integration

Uncomment Kafka services in `docker-compose.yml` and set:

```yaml
game:
  kafka:
    enabled: true
```

**Topics:**
- `trades.out` - Trade events published
- `marketdata.in` - External market data consumption

## Development

### Project Structure
```
src/main/java/com/example/marketmayhem/
├── config/          # WebSocket, CORS, Jackson config
├── controller/      # REST and WebSocket endpoints  
├── dto/            # Message and response objects
├── engine/         # Order book and matching logic
├── model/          # JPA entities (Order, Trade, etc.)
├── repo/           # Spring Data repositories
├── risk/           # Risk rule implementations
├── service/        # Business logic services
├── xml/            # JAXB IOI classes
└── client/         # JavaFX client application
```

### Adding New Features
1. **New Order Types**: Extend `OrderType` enum and matching logic
2. **Additional Risk Rules**: Implement `RiskRule` interface
3. **New Symbols**: Update `application.yml` configuration
4. **Custom Market Data**: Modify `MarketDataService`

## Performance Notes

- **Matching Engine**: Uses `StampedLock` for concurrent access
- **Order Book**: In-memory `NavigableMap` with FIFO queues
- **Database**: Selective persistence with JPA indexes
- **WebSocket**: Efficient JSON serialization with Jackson
- **Expected Performance**: ~10,000 matches/second on modern hardware

## License

MIT License - see [LICENSE](LICENSE) file.

## Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/new-feature`)
3. Commit changes (`git commit -am 'Add new feature'`)
4. Push to branch (`git push origin feature/new-feature`)
5. Create Pull Request

---

**Market Mayhem** - Where trading meets gaming! 🚀📈