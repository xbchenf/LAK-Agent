import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { guest: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/DefaultLayout.vue'),
      children: [
        {
          path: '',
          name: 'chat',
          component: () => import('@/views/ChatView.vue'),
        },
        {
          path: 'tickets',
          name: 'tickets',
          component: () => import('@/views/TicketView.vue'),
        },
        {
          path: 'knowledge',
          name: 'knowledge',
          component: () => import('@/views/knowledge/KnowledgeView.vue'),
        },
        {
          path: 'knowledge/:docId',
          name: 'knowledge-detail',
          component: () => import('@/views/knowledge/KnowledgeDetail.vue'),
        },
        {
          path: 'admin',
          name: 'admin',
          component: () => import('@/views/admin/AdminView.vue'),
          meta: { role: 'ADMIN' },
        },
        {
          path: 'admin/roles',
          name: 'admin-roles',
          component: () => import('@/views/admin/RoleManageView.vue'),
          meta: { role: 'ADMIN' },
        },
        {
          path: 'admin/users',
          name: 'admin-users',
          component: () => import('@/views/admin/UserManageView.vue'),
          meta: { role: 'ADMIN' },
        },
        {
          path: 'admin/audit',
          name: 'admin-audit',
          component: () => import('@/views/admin/AuditLogView.vue'),
          meta: { role: 'ADMIN' },
        },
        {
          path: 'admin/sensitive',
          name: 'admin-sensitive',
          component: () => import('@/views/admin/SensitiveWordView.vue'),
          meta: { role: 'ADMIN' },
        },
        {
          path: 'operator',
          name: 'operator',
          component: () => import('@/views/operator/OperatorDashboard.vue'),
        },
        {
          path: 'operator/tickets/:ticketNo',
          name: 'operator-ticket',
          component: () => import('@/views/operator/OperatorTicketDetail.vue'),
        },
        {
          path: 'operator/chat/:sessionId',
          name: 'operator-chat',
          component: () => import('@/views/operator/OperatorChatView.vue'),
        },
      ],
    },
  ],
})

router.beforeEach((to, _from) => {
  const token = localStorage.getItem('refreshToken')
  if (!token && !to.meta.guest) {
    return '/login'
  }
})

export default router
