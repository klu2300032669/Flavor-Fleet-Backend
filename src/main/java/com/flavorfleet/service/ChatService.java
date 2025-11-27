package com.flavorfleet.service;

import com.flavorfleet.dto.MenuItemDTO;
import com.flavorfleet.entity.ChatMessage;
import com.flavorfleet.repository.ChatMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ChatService {
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ChatMessageRepository chatRepository;
    private final MenuService menuService;

    @Value("${GROK_API_KEY:your-grok-api-key-here}")
    private String grokApiKey;

    @Value("${GROK_API_URL:https://api.x.ai/v1/chat/completions}")
    private String grokApiUrl;

    // Regex for price and type
    private static final Pattern PRICE_PATTERN = Pattern.compile("(above|under|below|best under)\\s*(\\d+)");
    private static final Pattern NON_VEG_PATTERN = Pattern.compile(".*non.*veg.*");
    private static final Pattern VEG_PATTERN = Pattern.compile(".*veg(?!.*non).*|.*vegan.*|.*vegetarian.*");
    private static final Pattern MENU_PATTERN = Pattern.compile(".*menu.*|.*food.*|.*show.*all.*");

    @Autowired
    public ChatService(ChatMessageRepository chatRepository, MenuService menuService) {
        this.chatRepository = chatRepository;
        this.menuService = menuService;
    }

    public String getGrokResponse(String userMessage, String userId) {
        String lower = userMessage.toLowerCase().trim();
        String response = null;

        // Detect type
        String type = null;
        if (NON_VEG_PATTERN.matcher(lower).matches()) type = "Non-Veg";
        else if (VEG_PATTERN.matcher(lower).matches()) type = "Veg";

        // Parse price range
        Matcher matcher = PRICE_PATTERN.matcher(lower);
        Double minPrice = null, maxPrice = null;
        boolean isBest = false;
        if (matcher.find()) {
            String op = matcher.group(1);
            double val = Double.parseDouble(matcher.group(2));
            if (op.equals("above")) minPrice = val;
            else if (op.equals("under") || op.equals("below")) maxPrice = val;
            else if (op.equals("best under")) {
                maxPrice = val;
                isBest = true;
            }
        }

        // Menu Query
        if (MENU_PATTERN.matcher(lower).matches() || minPrice != null || maxPrice != null || type != null) {
            response = getRealMenuResponse(type, minPrice, maxPrice, isBest);
        }

        // Custom responses
        if (lower.contains("how are you")) {
            response = "I'm buzzing with flavor energy! 🔥 How can I spice up your day? 😊";
        } else if (lower.contains("track") && lower.contains("order")) {
            response = "To track your order, check the app or email confirmation for the tracking link. Delivery in 30 mins! 🏃 Need help with order ID?";
        }

        // FALLBACK GROK
        if (response == null) {
            try {
                if (!grokApiKey.equals("your-grok-api-key-here")) {
                    logger.info("Fetching Grok for user: {} msg: {}", userId, userMessage);
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("Authorization", "Bearer " + grokApiKey);
                    headers.setContentType(MediaType.APPLICATION_JSON);

                    Map<String, Object> requestBody = new HashMap<>();
                    requestBody.put("model", "grok-4");
                    requestBody.put("messages", List.of(
                        Map.of("role", "system", "content", "You are FlavorFleet AI: Fun, food-savvy assistant. Respond concisely (<100 words), use emojis, focus on delivery (30min), menu (Indian/Vegan), orders, hours (10AM-11PM). End with a question!"),
                        Map.of("role", "user", "content", userMessage)
                    ));
                    requestBody.put("temperature", 0.8);
                    requestBody.put("max_tokens", 200);

                    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                    ResponseEntity<Map> apiResponse = restTemplate.exchange(grokApiUrl, HttpMethod.POST, entity, Map.class);

                    if (apiResponse.getStatusCode() == HttpStatus.OK) {
                        // FIXED #1: Correct List parsing
                        List<Map> choices = (List<Map>) apiResponse.getBody().get("choices");
                        Map<String, Object> choice = choices.get(0);
                        Map<String, Object> message = (Map<String, Object>) choice.get("message");
                        response = (String) message.get("content");
                        response = response.trim();
                    } else {
                        throw new RuntimeException("Grok error: " + apiResponse.getStatusCode());
                    }
                } else {
                    response = getMockResponse(userMessage, type);
                }
            } catch (Exception e) {
                logger.warn("Grok failed for {}: {}", userId, e.getMessage());
                response = getMockResponse(userMessage, type);
            }
        }

        // ALWAYS SAVE
        chatRepository.save(new ChatMessage(userId, userMessage, response));
        logger.info("SAVED chat for user: {}", userId);
        return response;
    }

    private String getRealMenuResponse(String type, Double minPrice, Double maxPrice, boolean isBest) {
        try {
            List<MenuItemDTO> items = menuService.getMenuItemsByTypeAndPrice(type, minPrice, maxPrice);
            if (items.isEmpty()) return "No matching items! Try different range. 😊 What else?";

            // Sort for "best"
            if (isBest) items = items.stream().sorted(Comparator.comparing(MenuItemDTO::getPrice)).collect(Collectors.toList());

            String title = (type == null ? "Menu" : type) + (minPrice != null ? " above ₹" + minPrice : "") + (maxPrice != null ? " under ₹" + maxPrice : "") + "! 🍛";
            String formatted = title + "\n" +
                items.stream().map(i -> "• " + i.getName() + " ₹" + i.getPrice() + " (" + i.getDescription() + ")")
                .collect(Collectors.joining("\n")) +
                "\n\nOrder? What next? 🚀";
            return formatted;
        } catch (Exception e) {
            // FIXED #4: Log DB errors
            logger.error("Menu query failed for type={}, minPrice={}, maxPrice={}: {}", type, minPrice, maxPrice, e.getMessage());
            return "Menu unavailable. Try again! 😋 What else?";
        }
    }

    // FIXED #3: Type-aware mock
    private String getMockResponse(String userInput, String type) {
        String lower = userInput.toLowerCase().trim();
        if (lower.contains("hi") || lower.contains("hello")) return "Hey! 👋 Delivery or menu?";
        if (lower.contains("delivery")) return "30 mins! 🏃 Free over ₹200.";
        if ("Non-Veg".equals(type)) return "Try Chicken Biryani ₹299! 😋 More?";
        if ("Veg".equals(type)) return "Try Paneer Tikka ₹249! 🌱 More?";
        return "Tasty! Try Butter Chicken ₹299. More? 😋";
    }
}