import { createRouter, createWebHistory } from 'vue-router'
import FileUploadView from '../views/FileUploadView.vue'
import FileListView from '../views/FileListView.vue'
import AdminView from '../views/AdminView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/upload' },
    { path: '/upload', component: FileUploadView, meta: { title: '파일 업로드' } },
    { path: '/files', component: FileListView, meta: { title: '처리 목록' } },
    { path: '/admin', component: AdminView, meta: { title: '관리자 대시보드' } },
  ],
})

export default router
