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
import AdminCommentForm from '../views/admin/AdminCommentForm.vue'

const routes = [
  { path: '/login', component: Login },
  { path: '/', component: UserHome, meta: { title: '棣栭〉', layout: 'user' } },
  { path: '/products', component: Products, meta: { title: '鍟嗗搧鍒楄〃', layout: 'user' } },
  { path: '/product/:productId', component: ProductDetail, meta: { title: '鍟嗗搧璇︽儏', layout: 'user' } },
  { path: '/recommend', component: Recommend, meta: { title: '涓€у寲鎺ㄨ崘', layout: 'user' } },
  { path: '/analysis/dashboard', component: Dashboard, meta: { title: '鏁版嵁鍒嗘瀽澶у睆', layout: 'user' } },
  { path: '/analysis/:type', component: Association, meta: { title: '鍏宠仈鍒嗘瀽', layout: 'user' } },
  { path: '/admin', component: AdminDashboard, meta: { title: '鎺у埗鍙?', layout: 'admin', admin: true } },
  { path: '/admin/comments/add', component: AdminCommentForm, meta: { title: '新增评论', layout: 'admin', admin: true } },
  { path: '/admin/:type', component: AdminList, meta: { title: '鏁版嵁绠＄悊', layout: 'admin', admin: true } }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path !== '/login' && !auth.token) return '/login'
  if (to.meta.admin && auth.user?.role !== 'admin') return '/'
})

export default router
