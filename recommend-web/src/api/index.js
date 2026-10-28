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
export const getHybridRecommend = (itemId, topN = 10) =>
  http.get('/recommend/hybrid', { params: { itemId, topN } })
export const evaluateRecommend = (topN = 10) =>
  http.get('/recommend/evaluate', { params: { topN } })

/* ===== 评价 ===== */
export const getReviews = (params) => http.get('/reviews', { params })
export const submitReview = (data) => http.post('/reviews', data)

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
export const getOrderDetail = (orderNo) => http.get(`/orders/${orderNo}`)
export const cancelOrder = (orderNo) => http.post(`/orders/${orderNo}/cancel`)

/* ===== 后台 ===== */
export const getStatistics = () => http.get('/admin/statistics')
export const getAdminProducts = (params) => http.get('/admin/products', { params })
export const createProduct = (data) => http.post('/admin/products', data)
export const updateProduct = (id, data) => http.put(`/admin/products/${id}`, data)
export const deleteProduct = (id) => http.delete(`/admin/products/${id}`)
export const updateProductStatus = (id, status) =>
  http.put(`/admin/products/${id}/status`, null, { params: { status } })
export const saveCategory = (data) => http.post('/admin/categories', data)
export const deleteCategory = (id) => http.delete(`/admin/categories/${id}`)
export const getAdminUsers = (params) => http.get('/admin/users', { params })
export const updateUserStatus = (id, status) =>
  http.put(`/admin/users/${id}/status`, null, { params: { status } })
export const getAdminOrders = (params) => http.get('/admin/orders', { params })
export const updateOrderStatus = (orderNo, status) =>
  http.put(`/admin/orders/${orderNo}/status`, null, { params: { status } })
