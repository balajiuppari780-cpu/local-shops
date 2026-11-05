/**
 * Local Shops Marketplace - api.js
 * Vanilla JavaScript API client and State Manager.
 */

const API_BASE = 'http://localhost:8080/api';

const api = {
    // -------------------------------------------------------------
    // Auth & Token Management
    // -------------------------------------------------------------
    getToken() {
        return localStorage.getItem('localshops_token');
    },

    setToken(token) {
        if (token) {
            localStorage.setItem('localshops_token', token);
        } else {
            localStorage.removeItem('localshops_token');
        }
    },

    getUser() {
        const u = localStorage.getItem('localshops_user');
        try {
            return u ? JSON.parse(u) : null;
        } catch (e) {
            return null;
        }
    },

    setUser(user) {
        if (user) {
            localStorage.setItem('localshops_user', JSON.stringify(user));
        } else {
            localStorage.removeItem('localshops_user');
        }
    },

    isLoggedIn() {
        return !!this.getToken();
    },

    logout() {
        this.setToken(null);
        this.setUser(null);
        window.location.href = 'auth.html';
    },

    // -------------------------------------------------------------
    // Generic HTTP Request Helper
    // -------------------------------------------------------------
    async request(endpoint, options = {}) {
        const headers = {
            'Content-Type': 'application/json',
            ...(options.headers || {})
        };

        const token = this.getToken();
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        const config = {
            ...options,
            headers
        };

        try {
            const response = await fetch(`${API_BASE}${endpoint}`, config);
            
            // Handle 204 No Content
            if (response.status === 204) {
                return null;
            }

            const data = await response.json().catch(() => null);

            if (!response.ok) {
                const message = (data && data.message) || (data && data.error) || `Request failed (${response.status})`;
                const err = new Error(message);
                err.status = response.status;
                err.data = data;
                throw err;
            }

            return data;
        } catch (err) {
            console.error(`API Error on [${options.method || 'GET'}] ${endpoint}:`, err);
            throw err;
        }
    },

    // -------------------------------------------------------------
    // Authentication Endpoints
    // -------------------------------------------------------------
    async login(email, password) {
        const data = await this.request('/auth/login', {
            method: 'POST',
            body: JSON.stringify({ email, password })
        });
        if (data && data.accessToken) {
            this.setToken(data.accessToken);
            this.setUser(data.user);
        }
        return data;
    },

    async signup(payload) {
        return this.request('/auth/signup', {
            method: 'POST',
            body: JSON.stringify(payload)
        });
    },

    async getMe() {
        return this.request('/auth/me');
    },

    // -------------------------------------------------------------
    // Shops Endpoints
    // -------------------------------------------------------------
    async getShops(area = null) {
        const params = new URLSearchParams();
        if (area) params.append('area', area);
        params.append('size', '50');
        return this.request(`/shops?${params.toString()}`);
    },

    async getShop(id) {
        return this.request(`/shops/${id}`);
    },

    async createShop(payload) {
        return this.request('/shops', {
            method: 'POST',
            body: JSON.stringify(payload)
        });
    },

    // -------------------------------------------------------------
    // Products Endpoints
    // -------------------------------------------------------------
    async getProducts(filters = {}) {
        const params = new URLSearchParams();
        if (filters.shopId) params.append('shopId', filters.shopId);
        if (filters.category && filters.category !== 'All') params.append('category', filters.category);
        if (filters.minPrice) params.append('minPrice', filters.minPrice);
        if (filters.maxPrice) params.append('maxPrice', filters.maxPrice);
        if (filters.inStock !== undefined && filters.inStock !== null) params.append('inStock', filters.inStock);
        if (filters.search) params.append('q', filters.search);
        if (filters.page !== undefined) params.append('page', filters.page);
        params.append('size', filters.size || 24);
        if (filters.sort) params.append('sort', filters.sort);

        return this.request(`/products?${params.toString()}`);
    },

    async getProduct(id) {
        return this.request(`/products/${id}`);
    },

    async createProduct(payload) {
        return this.request('/products', {
            method: 'POST',
            body: JSON.stringify(payload)
        });
    },

    async updateProduct(id, payload) {
        return this.request(`/products/${id}`, {
            method: 'PUT',
            body: JSON.stringify(payload)
        });
    },

    async deleteProduct(id) {
        return this.request(`/products/${id}`, {
            method: 'DELETE'
        });
    },

    // -------------------------------------------------------------
    // Orders Endpoints
    // -------------------------------------------------------------
    async createOrder(orderData, idempotencyKey = null) {
        const key = idempotencyKey || this.generateUUID();
        return this.request('/orders', {
            method: 'POST',
            headers: {
                'Idempotency-Key': key
            },
            body: JSON.stringify(orderData)
        });
    },

    async getOrder(id) {
        return this.request(`/orders/${id}`);
    },

    async getCustomerOrders() {
        return this.request('/orders/my-orders');
    },

    async getShopOrders(shopId) {
        return this.request(`/orders/shop/${shopId}`);
    },

    async updateOrderStatus(orderId, status) {
        return this.request(`/orders/${orderId}/status`, {
            method: 'PATCH',
            body: JSON.stringify({ status })
        });
    },

    async cancelOrder(orderId) {
        return this.request(`/orders/${orderId}/cancel`, {
            method: 'PATCH'
        });
    },

    // -------------------------------------------------------------
    // Cart & Single-Shop Enforcement (localStorage)
    // -------------------------------------------------------------
    getCart() {
        const raw = localStorage.getItem('localshops_cart');
        try {
            return raw ? JSON.parse(raw) : { shopId: null, shopName: null, items: [] };
        } catch (e) {
            return { shopId: null, shopName: null, items: [] };
        }
    },

    saveCart(cart) {
        localStorage.setItem('localshops_cart', JSON.stringify(cart));
        window.dispatchEvent(new CustomEvent('cart-updated', { detail: cart }));
    },

    addToCart(product, quantity = 1) {
        let cart = this.getCart();

        // Single-Shop check
        if (cart.shopId && cart.items.length > 0 && cart.shopId !== product.shopId) {
            return {
                conflict: true,
                currentShopName: cart.shopName || `Shop #${cart.shopId}`,
                newShopName: product.shopName || `Shop #${product.shopId}`,
                product,
                quantity
            };
        }

        // Set cart shop if empty
        if (!cart.shopId || cart.items.length === 0) {
            cart.shopId = product.shopId;
            cart.shopName = product.shopName || 'Local Shop';
            cart.items = [];
        }

        const existing = cart.items.find(i => i.productId === product.id);
        if (existing) {
            existing.quantity = Math.min(existing.quantity + quantity, product.stock);
        } else {
            cart.items.push({
                productId: product.id,
                name: product.name,
                price: Number(product.price),
                imageUrl: product.imageUrl || 'https://images.unsplash.com/photo-1542838132-92c53300491e?w=500&auto=format&fit=crop&q=60',
                stock: product.stock,
                category: product.category,
                quantity: Math.min(quantity, product.stock)
            });
        }

        this.saveCart(cart);
        return { success: true, cart };
    },

    updateCartQuantity(productId, quantity) {
        let cart = this.getCart();
        if (quantity <= 0) {
            cart.items = cart.items.filter(i => i.productId !== productId);
        } else {
            const item = cart.items.find(i => i.productId === productId);
            if (item) {
                item.quantity = Math.min(quantity, item.stock);
            }
        }

        if (cart.items.length === 0) {
            cart.shopId = null;
            cart.shopName = null;
        }

        this.saveCart(cart);
        return cart;
    },

    removeFromCart(productId) {
        return this.updateCartQuantity(productId, 0);
    },

    clearCart() {
        const empty = { shopId: null, shopName: null, items: [] };
        this.saveCart(empty);
        return empty;
    },

    getCartCount() {
        const cart = this.getCart();
        return cart.items.reduce((sum, item) => sum + item.quantity, 0);
    },

    getCartSubtotal() {
        const cart = this.getCart();
        return cart.items.reduce((sum, item) => sum + (item.price * item.quantity), 0);
    },

    // -------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------
    generateUUID() {
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
            const r = Math.random() * 16 | 0;
            const v = c === 'x' ? r : (r & 0x3 | 0x8);
            return v.toString(16);
        });
    },

    formatCurrency(amount) {
        return `$${Number(amount || 0).toFixed(2)}`;
    },

    showToast(message, type = 'success') {
        const existing = document.getElementById('localshops-toast');
        if (existing) existing.remove();

        const toast = document.createElement('div');
        toast.id = 'localshops-toast';
        const bg = type === 'error' ? 'bg-red-600' : (type === 'warning' ? 'bg-amber-600' : 'bg-emerald-600');
        toast.className = `fixed bottom-6 right-6 ${bg} text-white px-5 py-3 rounded-xl shadow-2xl z-50 flex items-center gap-3 transition-all duration-300 transform translate-y-0 text-sm font-medium`;
        toast.innerHTML = `
            <span class="material-symbols-outlined text-[20px]">${type === 'error' ? 'error' : (type === 'warning' ? 'warning' : 'check_circle')}</span>
            <span>${message}</span>
            <button onclick="this.parentElement.remove()" class="ml-2 hover:opacity-75">✕</button>
        `;
        document.body.appendChild(toast);
        setTimeout(() => {
            if (toast.parentElement) {
                toast.classList.add('opacity-0', 'translate-y-2');
                setTimeout(() => toast.remove(), 300);
            }
        }, 4000);
    }
};

window.api = api;
