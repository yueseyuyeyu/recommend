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

/**
 * 首页推荐流，游标分页，供无限滚动使用。
 *
 * 首次调用 cursor 传 null；之后把上一页返回的 nextCursor 原样回传。
 * 服务端只算一次排行池并缓存，翻页仅做切片，耗时与翻多深无关。
 */
export const getRecommendFeed = (cursor = null, pageSize = 10) =>
  http.get('/recommend/feed', { params: { cursor: cursor || undefined, pageSize } })

/* ===== 轮播图 ===== */
export const getCarousels = () => http.get('/carousels')

/* ===== 收藏 ===== */
export const addFavorite = (productId) => http.post(`/favorites/${productId}`)
export const removeFavorite = (productId) => http.delete(`/favorites/${productId}`)
export const getFavorites = (params) => http.get('/favorites', { params })
/** 一次性拿到当前用户收藏的全部商品 ID，用于列表页渲染红心 */
export const getFavoriteIds = () => http.get('/favorites/ids')
export const getFavoriteCount = () => http.get('/favorites/count')
export const checkFavorite = (productId) => http.get(`/favorites/check/${productId}`)

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

/* ===== 后台：轮播图管理 ===== */
export const getAdminCarousels = () => http.get('/admin/carousels')
export const createCarousel = (data) => http.post('/admin/carousels', data)
export const updateCarousel = (id, data) => http.put(`/admin/carousels/${id}`, data)
export const deleteCarousel = (id) => http.delete(`/admin/carousels/${id}`)
export const updateCarouselStatus = (id, status) =>
  http.put(`/admin/carousels/${id}/status`, null, { params: { status } })
