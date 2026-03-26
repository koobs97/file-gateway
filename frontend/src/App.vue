<script setup lang="ts">
import { useRoute } from 'vue-router'
const route = useRoute()
</script>

<template>
  <el-container class="layout">
    <el-header class="header">
      <span class="logo">File Gateway CDR</span>
      <el-menu mode="horizontal" :default-active="route.path" router>
        <el-menu-item index="/upload">파일 업로드</el-menu-item>
        <el-menu-item index="/files">처리 목록</el-menu-item>
        <el-menu-item index="/admin">관리자</el-menu-item>
      </el-menu>
    </el-header>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<style>
.layout { min-height: 100vh; background: #f5f7fa; }
.header {
  display: flex;
  align-items: center;
  gap: 32px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  padding: 0 24px;
}
.logo { font-size: 18px; font-weight: 700; color: #409eff; white-space: nowrap; }
.el-menu--horizontal { border-bottom: none; }
.el-main { padding: 24px; max-width: 1200px; margin: 0 auto; width: 100%; }
</style>
