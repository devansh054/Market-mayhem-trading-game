package com.example.marketmayhem.client;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.example.marketmayhem.dto.BookLevel;
import com.example.marketmayhem.dto.BookUpdate;
import com.example.marketmayhem.dto.CancelOrderMessage;
import com.example.marketmayhem.dto.MarketTick;
import com.example.marketmayhem.dto.PlaceOrderMessage;
import com.example.marketmayhem.dto.ScoreUpdate;
import com.example.marketmayhem.dto.TradeEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class MarketMayhemClient extends Application {
    
    private WebSocketStompClient stompClient;
    private StompSession stompSession;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // UI Components
    private TextField playerIdField;
    private TextField orderIdField;
    private ComboBox<String> symbolCombo;
    private ComboBox<String> sideCombo;
    private TextField qtyField;
    private TextField priceField;
    private ComboBox<String> typeCombo;
    private Button placeOrderButton;
    private Button cancelOrderButton;
    
    private TableView<BookLevel> bidsTable;
    private TableView<BookLevel> asksTable;
    private TableView<TradeDisplay> tradesTable;
    private Label pnlLabel;
    private Label violationsLabel;
    private TextArea logArea;
    
    private ObservableList<BookLevel> bids = FXCollections.observableArrayList();
    private ObservableList<BookLevel> asks = FXCollections.observableArrayList();
    private ObservableList<TradeDisplay> trades = FXCollections.observableArrayList();
    
    private String currentRoomId = "1";
    private BigDecimal currentPnl = BigDecimal.ZERO;
    private int currentViolations = 0;
    
    @Override
    public void start(Stage primaryStage) {
        objectMapper.registerModule(new JavaTimeModule());
        
        primaryStage.setTitle("Market Mayhem Trading Client");
        
        // Create UI
        VBox root = createMainLayout();
        Scene scene = new Scene(root, 1200, 800);
        primaryStage.setScene(scene);
        primaryStage.show();
        
        // Connect to WebSocket
        connectToWebSocket();
        
        primaryStage.setOnCloseRequest(e -> {
            if (stompSession != null) {
                stompSession.disconnect();
            }
            Platform.exit();
        });
    }
    
    private VBox createMainLayout() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        
        // Connection panel
        HBox connectionPanel = createConnectionPanel();
        
        // Order entry panel
        VBox orderPanel = createOrderPanel();
        
        // Market data panel
        HBox marketDataPanel = createMarketDataPanel();
        
        // Status panel
        HBox statusPanel = createStatusPanel();
        
        // Log area
        logArea = new TextArea();
        logArea.setPrefRowCount(8);
        logArea.setEditable(false);
        
        root.getChildren().addAll(
            connectionPanel,
            orderPanel,
            marketDataPanel,
            statusPanel,
            new Label("Log:"),
            logArea
        );
        
        return root;
    }
    
    private HBox createConnectionPanel() {
        HBox panel = new HBox(10);
        panel.setPadding(new Insets(5));
        
        playerIdField = new TextField("Player1");
        playerIdField.setPromptText("Player ID");
        
        Button connectButton = new Button("Connect");
        connectButton.setOnAction(e -> connectToWebSocket());
        
        panel.getChildren().addAll(
            new Label("Player ID:"), playerIdField,
            connectButton
        );
        
        return panel;
    }
    
    private VBox createOrderPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(5));
        panel.setStyle("-fx-border-color: gray; -fx-border-width: 1;");
        
        Label title = new Label("Order Entry");
        title.setStyle("-fx-font-weight: bold;");
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(5);
        
        // Order fields
        orderIdField = new TextField();
        orderIdField.setPromptText("Order ID");
        
        symbolCombo = new ComboBox<>();
        symbolCombo.getItems().addAll("AAPL", "MSFT");
        symbolCombo.setValue("AAPL");
        
        sideCombo = new ComboBox<>();
        sideCombo.getItems().addAll("BUY", "SELL");
        sideCombo.setValue("BUY");
        
        qtyField = new TextField("100");
        qtyField.setPromptText("Quantity");
        
        priceField = new TextField("189.50");
        priceField.setPromptText("Price");
        
        typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("LIMIT", "MARKET");
        typeCombo.setValue("LIMIT");
        
        // Add to grid
        grid.add(new Label("Order ID:"), 0, 0);
        grid.add(orderIdField, 1, 0);
        grid.add(new Label("Symbol:"), 2, 0);
        grid.add(symbolCombo, 3, 0);
        
        grid.add(new Label("Side:"), 0, 1);
        grid.add(sideCombo, 1, 1);
        grid.add(new Label("Quantity:"), 2, 1);
        grid.add(qtyField, 3, 1);
        
        grid.add(new Label("Type:"), 0, 2);
        grid.add(typeCombo, 1, 2);
        grid.add(new Label("Price:"), 2, 2);
        grid.add(priceField, 3, 2);
        
        // Buttons
        HBox buttonBox = new HBox(10);
        placeOrderButton = new Button("Place Order");
        placeOrderButton.setOnAction(e -> placeOrder());
        placeOrderButton.setDisable(true);
        
        cancelOrderButton = new Button("Cancel Order");
        cancelOrderButton.setOnAction(e -> cancelOrder());
        cancelOrderButton.setDisable(true);
        
        buttonBox.getChildren().addAll(placeOrderButton, cancelOrderButton);
        
        panel.getChildren().addAll(title, grid, buttonBox);
        return panel;
    }
    
    private HBox createMarketDataPanel() {
        HBox panel = new HBox(10);
        panel.setPadding(new Insets(5));
        
        // Order book
        VBox bookPanel = new VBox(5);
        bookPanel.setStyle("-fx-border-color: gray; -fx-border-width: 1;");
        
        Label bookTitle = new Label("Order Book");
        bookTitle.setStyle("-fx-font-weight: bold;");
        
        // Bids table
        bidsTable = createBookTable("Bids");
        bidsTable.setItems(bids);
        
        // Asks table  
        asksTable = createBookTable("Asks");
        asksTable.setItems(asks);
        
        bookPanel.getChildren().addAll(bookTitle, 
            new Label("Asks:"), asksTable,
            new Label("Bids:"), bidsTable);
        
        // Trades table
        VBox tradesPanel = new VBox(5);
        tradesPanel.setStyle("-fx-border-color: gray; -fx-border-width: 1;");
        
        Label tradesTitle = new Label("Recent Trades");
        tradesTitle.setStyle("-fx-font-weight: bold;");
        
        tradesTable = createTradesTable();
        tradesTable.setItems(trades);
        
        tradesPanel.getChildren().addAll(tradesTitle, tradesTable);
        
        panel.getChildren().addAll(bookPanel, tradesPanel);
        return panel;
    }
    
    private TableView<BookLevel> createBookTable(String title) {
        TableView<BookLevel> table = new TableView<>();
        table.setPrefHeight(150);
        
        TableColumn<BookLevel, BigDecimal> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));
        
        TableColumn<BookLevel, Long> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("qty"));
        
        table.getColumns().addAll(priceCol, qtyCol);
        return table;
    }
    
    private TableView<TradeDisplay> createTradesTable() {
        TableView<TradeDisplay> table = new TableView<>();
        table.setPrefHeight(200);
        
        TableColumn<TradeDisplay, String> symbolCol = new TableColumn<>("Symbol");
        symbolCol.setCellValueFactory(new PropertyValueFactory<>("symbol"));
        
        TableColumn<TradeDisplay, Long> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("qty"));
        
        TableColumn<TradeDisplay, BigDecimal> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));
        
        TableColumn<TradeDisplay, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(new PropertyValueFactory<>("time"));
        
        table.getColumns().addAll(symbolCol, qtyCol, priceCol, timeCol);
        return table;
    }
    
    private HBox createStatusPanel() {
        HBox panel = new HBox(20);
        panel.setPadding(new Insets(5));
        panel.setStyle("-fx-border-color: gray; -fx-border-width: 1;");
        
        pnlLabel = new Label("P&L: $0.00");
        pnlLabel.setStyle("-fx-font-weight: bold;");
        
        violationsLabel = new Label("Violations: 0");
        violationsLabel.setStyle("-fx-font-weight: bold;");
        
        panel.getChildren().addAll(pnlLabel, violationsLabel);
        return panel;
    }
    
    private void connectToWebSocket() {
        try {
            stompClient = new WebSocketStompClient(new StandardWebSocketClient());
            MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
            converter.setObjectMapper(objectMapper);
            stompClient.setMessageConverter(converter);
            
            StompSessionHandler sessionHandler = new MyStompSessionHandler();
            
            CompletableFuture<StompSession> sessionFuture = 
                stompClient.connectAsync("ws://localhost:8080/ws", sessionHandler);
                
            stompSession = sessionFuture.get();
            
            Platform.runLater(() -> {
                placeOrderButton.setDisable(false);
                cancelOrderButton.setDisable(false);
                log("Connected to WebSocket server");
            });
            
            subscribeToTopics();
            
        } catch (Exception e) {
            Platform.runLater(() -> log("Failed to connect: " + e.getMessage()));
        }
    }
    
    private void subscribeToTopics() {
        try {
            // Subscribe to book updates for AAPL
            stompSession.subscribe("/topic/room/" + currentRoomId + "/book/AAPL", 
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return BookUpdate.class;
                    }
                    
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        BookUpdate update = (BookUpdate) payload;
                        Platform.runLater(() -> updateOrderBook(update));
                    }
                });
            
            // Subscribe to book updates for MSFT
            stompSession.subscribe("/topic/room/" + currentRoomId + "/book/MSFT", 
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return BookUpdate.class;
                    }
                    
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        BookUpdate update = (BookUpdate) payload;
                        Platform.runLater(() -> updateOrderBook(update));
                    }
                });
            
            // Subscribe to trades
            stompSession.subscribe("/topic/room/" + currentRoomId + "/trades", 
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return TradeEvent.class;
                    }
                    
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        TradeEvent trade = (TradeEvent) payload;
                        Platform.runLater(() -> addTrade(trade));
                    }
                });
            
            // Subscribe to market ticks
            stompSession.subscribe("/topic/room/" + currentRoomId + "/ticks", 
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return MarketTick.class;
                    }
                    
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        MarketTick tick = (MarketTick) payload;
                        Platform.runLater(() -> log("Market tick: " + tick.symbol() + 
                                                  " bid=" + tick.bid() + 
                                                  " ask=" + tick.ask() + 
                                                  " last=" + tick.last()));
                    }
                });
            
            // Subscribe to score updates
            stompSession.subscribe("/topic/scores", 
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return ScoreUpdate.class;
                    }
                    
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        ScoreUpdate score = (ScoreUpdate) payload;
                        Platform.runLater(() -> updateScore(score));
                    }
                });
            
        } catch (Exception e) {
            Platform.runLater(() -> log("Failed to subscribe: " + e.getMessage()));
        }
    }
    
    private void placeOrder() {
        if (stompSession == null || !stompSession.isConnected()) {
            log("Not connected to server");
            return;
        }
        
        try {
            String orderId = orderIdField.getText().trim();
            if (orderId.isEmpty()) {
                orderId = "ORDER_" + System.currentTimeMillis();
                orderIdField.setText(orderId);
            }
            
            PlaceOrderMessage order = new PlaceOrderMessage(
                playerIdField.getText().trim(),
                orderId,
                symbolCombo.getValue(),
                sideCombo.getValue().equals("BUY") ? 
                    com.example.marketmayhem.model.Side.BUY : 
                    com.example.marketmayhem.model.Side.SELL,
                Long.parseLong(qtyField.getText().trim()),
                typeCombo.getValue().equals("LIMIT") ? 
                    com.example.marketmayhem.model.OrderType.LIMIT : 
                    com.example.marketmayhem.model.OrderType.MARKET,
                typeCombo.getValue().equals("LIMIT") ? 
                    new BigDecimal(priceField.getText().trim()) : null
            );
            
            stompSession.send("/app/room/" + currentRoomId + "/order.place", order);
            log("Order placed: " + orderId);
            
        } catch (Exception e) {
            log("Error placing order: " + e.getMessage());
        }
    }
    
    private void cancelOrder() {
        if (stompSession == null || !stompSession.isConnected()) {
            log("Not connected to server");
            return;
        }
        
        try {
            String orderId = orderIdField.getText().trim();
            if (orderId.isEmpty()) {
                log("Please enter order ID to cancel");
                return;
            }
            
            CancelOrderMessage cancel = new CancelOrderMessage(
                playerIdField.getText().trim(),
                orderId
            );
            
            stompSession.send("/app/room/" + currentRoomId + "/order.cancel", cancel);
            log("Cancel request sent for order: " + orderId);
            
        } catch (Exception e) {
            log("Error canceling order: " + e.getMessage());
        }
    }
    
    private void updateOrderBook(BookUpdate update) {
        if (symbolCombo.getValue().equals(update.symbol())) {
            bids.clear();
            asks.clear();
            bids.addAll(update.bids());
            asks.addAll(update.asks());
        }
    }
    
    private void addTrade(TradeEvent trade) {
        TradeDisplay display = new TradeDisplay(
            trade.symbol(),
            trade.qty(),
            trade.price(),
            trade.executedAt().toString()
        );
        
        trades.add(0, display); // Add to top
        if (trades.size() > 100) {
            trades.remove(trades.size() - 1); // Keep only recent trades
        }
        
        log("Trade executed: " + trade.symbol() + " " + trade.qty() + "@" + trade.price());
    }
    
    private void updateScore(ScoreUpdate score) {
        if (score.playerId().equals(playerIdField.getText().trim())) {
            currentPnl = score.pnl();
            currentViolations = score.violations();
            
            pnlLabel.setText("P&L: $" + currentPnl.toString());
            violationsLabel.setText("Violations: " + currentViolations);
        }
    }
    
    private void log(String message) {
        String timestamp = DateTimeFormatter.ofPattern("HH:mm:ss").format(Instant.now().atZone(java.time.ZoneId.systemDefault()));
        logArea.appendText("[" + timestamp + "] " + message + "\n");
    }
    
    public static void main(String[] args) {
        launch(args);
    }
    
    // Inner classes
    private static class MyStompSessionHandler extends StompSessionHandlerAdapter {
        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            // Connection established
        }
        
        @Override
        public void handleException(StompSession session, StompCommand command, 
                                  StompHeaders headers, byte[] payload, Throwable exception) {
            exception.printStackTrace();
        }
    }
    
    public static class TradeDisplay {
        private final String symbol;
        private final Long qty;
        private final BigDecimal price;
        private final String time;
        
        public TradeDisplay(String symbol, Long qty, BigDecimal price, String time) {
            this.symbol = symbol;
            this.qty = qty;
            this.price = price;
            this.time = time.length() > 19 ? time.substring(11, 19) : time;
        }
        
        public String getSymbol() { return symbol; }
        public Long getQty() { return qty; }
        public BigDecimal getPrice() { return price; }
        public String getTime() { return time; }
    }
}