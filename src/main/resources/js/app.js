<!-- public/app.js -->
class WatchlistApp {
    constructor() {
        this.userId = 'user1'; // Should come from auth system
        this.stompClient = null;
        this.watchlistEl = document.getElementById('watchlist');
        this.emptyStateEl = document.getElementById('empty-state');
        this.searchEl = document.getElementById('search');
        this.sugEl = document.getElementById('suggestions');
        this.watchlistCountEl = document.getElementById('watchlist-count');

        this.connectWebSocket();
        this.setupEventListeners();
        this.fetchInitialWatchlist();
    }

    connectWebSocket() {
        const socket = new SockJS('/ws');
        this.stompClient = Stomp.over(socket);

        this.stompClient.connect({}, () => {
            // Price updates
            this.stompClient.subscribe('/topic/prices', this.handlePriceUpdate.bind(this));

            // User-specific watchlist updates
            this.stompClient.subscribe(`/user/queue/watchlist`, this.handleWatchlistEvent.bind(this));
        });
    }

    handlePriceUpdate(message) {
        const data = JSON.parse(message.body);
        const card = document.getElementById(`card-${data.symbol}`);
        if (!card) return;

        card.querySelector('.ltp').textContent = `$${data.ltp.toFixed(2)}`;
        const chEl = card.querySelector('.change_pct');
        chEl.textContent = `${data.change24 >= 0 ? '+' : ''}${data.change24.toFixed(2)}%`;
        chEl.className = `change_pct ${data.change24 >= 0 ? 'positive' : 'negative'}`;
    }

    handleWatchlistEvent(message) {
        const event = JSON.parse(message.body);
        if (event.type === 'ADD') {
            this.addToWatchlistUI(event.symbol);
        } else if (event.type === 'REMOVE') {
            this.removeFromWatchlistUI(event.symbol);
        }
    }

    async fetchInitialWatchlist() {
        try {
            const response = await fetch(`/api/watchlist/${this.userId}`);
            const watchlist = await response.json();

            watchlist.forEach(item => {
                this.addToWatchlistUI(item.symbol);
            });

            this.updateEmptyState();
        } catch (error) {
            console.error('Failed to load watchlist:', error);
        }
    }

    addToWatchlistUI(symbol) {
        if (document.getElementById(`card-${symbol}`)) return;

        const assetType = symbol.includes('-USD') ? 'Crypto' : 'Stocks';
        const card = document.createElement('div');
        card.className = 'card';
        card.id = `card-${symbol}`;
        card.innerHTML = `
            <button class="close-btn" data-symbol="${symbol}">
                <i class="material-icons">close</i>
            </button>
            <div class="card-header">
                <h2>${symbol}</h2>
                <div class="card-badge">${assetType}</div>
            </div>
            <div class="ltp">$--.--</div>
            <div class="change-container">
                <div class="change_pct">+0.00%</div>
                <span>24h change</span>
            </div>
        `;

        this.watchlistEl.prepend(card);
        card.querySelector('.close-btn').addEventListener('click', () => {
            this.removeSymbol(symbol);
        });

        this.updateEmptyState();
    }

    removeFromWatchlistUI(symbol) {
        const card = document.getElementById(`card-${symbol}`);
        if (card) {
            card.remove();
            this.updateEmptyState();
        }
    }

    updateEmptyState() {
        const hasItems = this.watchlistEl.children.length > 0;
        this.emptyStateEl.style.display = hasItems ? 'none' : 'flex';
        this.watchlistCountEl.textContent = this.watchlistEl.children.length;
    }

    setupEventListeners() {
        this.searchEl.addEventListener('input', this.handleSearch.bind(this));
        document.addEventListener('click', (e) => {
            if (!this.searchEl.contains(e.target)) {
                this.sugEl.style.display = 'none';
            }
        });
    }

    async handleSearch(e) {
        const query = e.target.value.trim();
        if (!query) {
            this.sugEl.style.display = 'none';
            return;
        }

        try {
            const products = await this.searchProducts(query);
            this.renderSuggestions(products);
        } catch (error) {
            console.error('Search failed:', error);
            this.sugEl.style.display = 'none';
        }
    }

    async searchProducts(query) {
        const params = new URLSearchParams({
            symbol: query,
            contract_types: 'perpetual_futures',
            states: 'live'
        });

        const response = await fetch(`https://api.india.delta.exchange/v2/products?${params}`);
        if (!response.ok) throw new Error('API error');

        const data = await response.json();
        return data.result || [];
    }

    renderSuggestions(products) {
        if (!products.length) {
            this.sugEl.style.display = 'none';
            return;
        }

        this.sugEl.innerHTML = '';
        products.forEach(product => {
            const div = document.createElement('div');
            div.className = 'suggestion';
            div.innerHTML = `
                <span>${product.symbol} — ${product.description}</span>
                <span class="material-icons">add_circle_outline</span>
            `;
            div.addEventListener('click', () => {
                this.addSymbol(product.symbol);
                this.searchEl.value = '';
                this.sugEl.style.display = 'none';
            });
            this.sugEl.appendChild(div);
        });
        this.sugEl.style.display = 'block';
    }

    addSymbol(symbol) {
        this.stompClient.send('/app/add', {}, JSON.stringify({
            symbol: symbol,
            userId: this.userId
        }));
    }

    removeSymbol(symbol) {
        this.stompClient.send('/app/remove', {}, JSON.stringify({
            symbol: symbol,
            userId: this.userId
        }));
    }
}

// Initialize app
document.addEventListener('DOMContentLoaded', () => {
    new WatchlistApp();
});