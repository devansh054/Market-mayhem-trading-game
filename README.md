![image alt](https://github.com/devansh054/Market-mayhem-trading-game/blob/04414337a8459d9869912d51ad26d6e7aec11c7e/APP_U1_2.jpeg)


# Market Mayhem - Multiplayer Trading Game 📈

🚀 Real-time multiplayer stock trading game with WebSocket connections, dynamic room system, and live P&L tracking. Built with Spring Boot backend and modern responsive frontend.

## Features ✨

- **Real-time multiplayer trading** - Trade with other players in real-time
- **WebSocket connections** - Live market data and trade execution
- **Dynamic room system** - Multiple rooms with different player capacities
- **Accurate P&L tracking** - Real-time profit/loss calculations
- **Modern UI** - Clean, responsive interface
- **Order matching engine** - Proper trade execution between players

## Room System 🏠

- **Room Gamma**: 1 player max
- **Room Alpha**: 2 players max  
- **Room Beta**: 5 players max
- **VIP Room**: 3 players max
- **Custom rooms**: 3 players default

## Tech Stack 🛠️

### Backend
- **Spring Boot** - Java web framework
- **WebSocket** - Real-time communication
- **STOMP** - Messaging protocol
- **Maven** - Dependency management
- **Database** - Postgresql

### Frontend
- **HTML5** - Structure
- **CSS3** - Modern styling with gradients
- **JavaScript** - Interactive functionality
- **STOMP.js** - WebSocket client

## Getting Started 🚀

### Prerequisites
- Java 11 or higher
- Maven 3.6+

### Running the Backend
```bash
cd market-mayhem
./mvnw spring-boot:run
```

The backend will start on `http://localhost:8080`

### Running the Frontend
1. Open `stomp-test.html` in your browser
2. Enter your player name
3. Select a trading room
4. Start trading!

## How to Play 🎮

1. **Join a room** - Select from available rooms or create a custom one
2. **Place orders** - Buy/Sell stocks with Market or Limit orders
3. **Trade with others** - Your orders match with other players' orders
4. **Track P&L** - Monitor your profit/loss in real-time
5. **View trades** - See all executed trades in the trade log

## Architecture 🏗️

- **WebSocket Controller** - Handles real-time messaging
- **Order Matching Engine** - Matches buy/sell orders between players
- **Room Management** - Dynamic room capacity and player tracking
- **Market Data** - Real-time stock price updates


## 🔗 Live Demo
Game URL: https://dazzling-vacherin-33f191.netlify.app
Backend API: https://market-mayhem-trading-game-7.onrender.com

## Development 👨‍💻

The game uses localStorage for client-side room management and WebSockets for real-time communication between the Spring Boot backend and multiple browser clients.

## Contributing 🤝

Feel free to submit issues and enhancement requests!

## License 📄

This project is licensed under the MIT License.

![image alt](https://github.com/devansh054/Market-mayhem-trading-game/blob/b8e0b0216de54c5f7ca82ddcfee59b173aca7166/APP_UI.jpeg)
