package com.example.marketmayhem.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.marketmayhem.dto.LeaderboardResponse;
import com.example.marketmayhem.service.IoiService;
import com.example.marketmayhem.service.LeaderboardService;
import com.example.marketmayhem.xml.IOI;

import jakarta.xml.bind.JAXBException;

@RestController
@RequestMapping("/api")
public class ApiController {
    
    private static final Logger log = LoggerFactory.getLogger(ApiController.class);
    
    private final LeaderboardService leaderboardService;
    private final IoiService ioiService;
    
    public ApiController(LeaderboardService leaderboardService, IoiService ioiService) {
        this.leaderboardService = leaderboardService;
        this.ioiService = ioiService;
    }
    
    @GetMapping("/leaderboard")
    public ResponseEntity<LeaderboardResponse> getLeaderboard() {
        try {
            LeaderboardResponse leaderboard = leaderboardService.getLeaderboard();
            return ResponseEntity.ok(leaderboard);
        } catch (Exception e) {
            log.error("Error getting leaderboard", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping(value = "/ioi/xml", consumes = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> storeIoi(@RequestBody String ioiXml) {
        try {
            IOI ioi = ioiService.parseIoiFromXml(ioiXml);
            ioiService.storeIoi(ioi);
            log.info("Stored IOI: {}", ioi.getIoiId());
            return ResponseEntity.ok("IOI stored successfully");
        } catch (JAXBException e) {
            log.error("Error parsing IOI XML", e);
            return ResponseEntity.badRequest().body("Invalid XML format");
        } catch (Exception e) {
            log.error("Error storing IOI", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to store IOI");
        }
    }
    
    @GetMapping(value = "/ioi", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> getIois(@RequestParam(required = false) String symbol) {
        try {
            List<IOI> iois;
            if (symbol != null && !symbol.isEmpty()) {
                iois = ioiService.getIoisBySymbol(symbol);
            } else {
                // Return sample IOI for demonstration
                IOI sampleIoi = ioiService.createSampleIoi(symbol != null ? symbol : "AAPL");
                iois = List.of(sampleIoi);
            }
            
            if (iois.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            // For simplicity, return the first IOI as XML
            String xml = ioiService.convertIoiToXml(iois.get(0));
            return ResponseEntity.ok(xml);
            
        } catch (JAXBException e) {
            log.error("Error converting IOI to XML", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Error getting IOIs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}