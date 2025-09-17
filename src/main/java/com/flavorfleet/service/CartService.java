	package com.flavorfleet.service;
	
	import com.flavorfleet.entity.CartItem;
	import com.flavorfleet.entity.User;
	import com.flavorfleet.repository.CartItemRepository;
	import org.slf4j.Logger;
	import org.slf4j.LoggerFactory;
	import org.springframework.stereotype.Service;
	import org.springframework.transaction.annotation.Transactional;
	
	import java.util.List;
	
	@Service
	public class CartService {
	    private static final Logger logger = LoggerFactory.getLogger(CartService.class);
	
	    private final CartItemRepository cartItemRepository;
	    private final UserService userService;
	
	    public CartService(CartItemRepository cartItemRepository, UserService userService) {
	        this.cartItemRepository = cartItemRepository;
	        this.userService = userService;
	    }
	
	    @Transactional(readOnly = true)
	    public List<CartItem> getCartItems(String email) {
	        return userService.getCartItems(email);
	    }
	
	    @Transactional
	    public CartItem addToCart(String email, CartItem cartItem) {
	        return userService.addToCart(email, cartItem);
	    }
	
	    @Transactional
	    public CartItem updateCartItem(String email, CartItem cartItem) {
	        return userService.updateCartItem(email, cartItem);
	    }
	
	    @Transactional
	    public boolean removeFromCart(String email, Long id) {
	        return userService.removeFromCart(email, id);
	    }
	
	    @Transactional
	    public void clearCart(String email) {
	        userService.clearCart(email);
	    }
	}
	// No changes needed - service delegates appropriately