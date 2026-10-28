import http from './http'

/* ===== 认证 ===== */
export const login = (data) => http.post('/auth/login', data)
export const register = (data) => http.post('/auth/register', data)

/* ===== 商品与类目 ===== */
export const getProducts = (params) => http.get('/products', { params })
export const getProduct = (id) => http.get(`/products/${id}`)
export const getCategories = () => http.get('/categories')

/* ===== 推荐 ===== */
export const getHomeRecommend = (topN = 10) =>
  http.get('/recommend/home', { params: { topN } })
export const getHotRecommend = (topN = 10) =>
  http.get('/recommend/hot', { params: { topN } })
export const getSimilarRecommend = (productId, topN = 10) =>
  http.get(`/recommend/similar/${productId}`, { params: { topN } })

/* ===== 评价 ===== */
export const getReviews = (params) => http.get('/reviews', { params })

/* ===== 行为上报 ===== */
export const reportBehavior = (data) => http.post('/behavior/report', data)

/* ===== 购物车 ===== */
export const getCart = () => http.get('/cart')
export const addCart = (data) => http.post('/cart', data)
export const updateCart = (cartId, data) => http.put(`/cart/${cartId}`, data)
export const removeCart = (cartId) => http.delete(`/cart/${cartId}`)
export const clearCart = () => http.delete('/cart')
export const getCartTotal = () => http.get('/cart/total')

/* ===== 订单 ===== */
export const createOrder = (data) => http.post('/orders', data)
export const getOrders = (params) => http.get('/orders', { params })
export const cancelOrder = (orderNo) => http.post(`/orders/${orderNo}/cancel`)

/* ===== 后台 ===== */
export const getStatistics = () => http.get('/admin/statistics')
export const updateProductStatus = (id, status) =>
  http.put(`/admin/products/${id}/status`, null, { params: { status } })
