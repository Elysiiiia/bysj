import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import Login from '../views/Login.vue'
import UserHome from '../views/UserHome.vue'
import Products from '../views/Products.vue'
import ProductDetail from '../views/ProductDetail.vue'
import Recommend from '../views/Recommend.vue'
import Dashboard from '../views/analysis/Dashboard.vue'
import Association from '../views/analysis/Association.vue'
import AdminDashboard from '../views/admin/AdminDashboard.vue'
import AdminList from '../views/admin/AdminList.vue'

const routes = [
  { path: '/login', component: Login },
  { path: '/', component: UserHome, meta: { title: '首页', layout: 'user' } },
  { path: '/products', component: Products, meta: { title: '商品列表', layout: 'user' } },
  { path: '/product/:productId', component: ProductDetail, meta: { title: '商品详情', layout: 'user' } },
  { path: '/recommend', component: Recommend, meta: { title: '个性化推荐', layout: 'user' } },
  { path: '/analysis/dashboard', component: Dashboard, meta: { title: '数据分析大屏', layout: 'user' } },
  { path: '/analysis/:type', component: Association, meta: { title: '关联分析', layout: 'user' } },
  { path: '/admin', component: AdminDashboard, meta: { title: '控制台', layout: 'admin', admin: true } },
  { path: '/admin/:type', component: AdminList, meta: { title: '数据管理', layout: 'admin', admin: true } }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path !== '/login' && !auth.token) return '/login'
  if (to.meta.admin && auth.user?.role !== 'admin') return '/'
})

export default router
