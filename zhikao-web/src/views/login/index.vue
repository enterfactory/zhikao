<template>
  <div class="login">
    <!-- ==================== 左侧：品牌与氛围 ==================== -->
    <section class="login__brand">
      <!--
        背景是内联 SVG 而不是位图。原因有三：
        1. 矢量在任何分辨率（含 4K、Retina）下都锐利，位图要准备多套尺寸；
        2. 体积几乎为 0，且不产生额外网络请求，登录页首屏因此不会因为背景图而抖动；
        3. 配色直接引用品牌色梯度，换主题时不会出现「背景还是蓝色、按钮已变绿」的割裂。
        构图刻意抽象（网格 + 光斑 + 卡片轮廓 + 柱状/折线），不放任何文字，
        避免出现 AI 配图常见的乱码字与廉价感。
      -->
      <svg class="login__art" viewBox="0 0 800 900" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
        <defs>
          <linearGradient id="bgBase" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#0b1729" />
            <stop offset="55%" stop-color="#122a52" />
            <stop offset="100%" stop-color="#1b3f86" />
          </linearGradient>
          <radialGradient id="glowA" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stop-color="#2f6bff" stop-opacity="0.55" />
            <stop offset="100%" stop-color="#2f6bff" stop-opacity="0" />
          </radialGradient>
          <radialGradient id="glowB" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stop-color="#0ea5e9" stop-opacity="0.38" />
            <stop offset="100%" stop-color="#0ea5e9" stop-opacity="0" />
          </radialGradient>
          <radialGradient id="glowC" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stop-color="#7c5cff" stop-opacity="0.34" />
            <stop offset="100%" stop-color="#7c5cff" stop-opacity="0" />
          </radialGradient>
          <pattern id="dots" width="26" height="26" patternUnits="userSpaceOnUse">
            <circle cx="1.5" cy="1.5" r="1.5" fill="#ffffff" fill-opacity="0.10" />
          </pattern>
          <linearGradient id="cardFill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stop-color="#ffffff" stop-opacity="0.16" />
            <stop offset="100%" stop-color="#ffffff" stop-opacity="0.05" />
          </linearGradient>
          <linearGradient id="barFill" x1="0" y1="1" x2="0" y2="0">
            <stop offset="0%" stop-color="#2f6bff" stop-opacity="0.9" />
            <stop offset="100%" stop-color="#7cc4ff" stop-opacity="0.9" />
          </linearGradient>
        </defs>

        <rect width="800" height="900" fill="url(#bgBase)" />
        <rect width="800" height="900" fill="url(#dots)" />
        <circle cx="130" cy="150" r="300" fill="url(#glowA)" />
        <circle cx="700" cy="620" r="280" fill="url(#glowB)" />
        <circle cx="420" cy="820" r="260" fill="url(#glowC)" />

        <!-- 抽象「考试数据」卡片：只有轮廓与图表形状，不含任何文字 -->
        <g class="login__art-card login__art-card--a">
          <rect x="70" y="470" width="300" height="180" rx="18" fill="url(#cardFill)" stroke="#ffffff" stroke-opacity="0.24" />
          <rect x="98" y="580" width="26" height="44" rx="6" fill="url(#barFill)" />
          <rect x="136" y="556" width="26" height="68" rx="6" fill="url(#barFill)" />
          <rect x="174" y="600" width="26" height="24" rx="6" fill="url(#barFill)" />
          <rect x="212" y="540" width="26" height="84" rx="6" fill="url(#barFill)" />
          <rect x="250" y="568" width="26" height="56" rx="6" fill="url(#barFill)" />
          <rect x="288" y="524" width="26" height="100" rx="6" fill="url(#barFill)" />
        </g>

        <g class="login__art-card login__art-card--b">
          <rect x="420" y="330" width="300" height="150" rx="18" fill="url(#cardFill)" stroke="#ffffff" stroke-opacity="0.24" />
          <polyline
            points="450,440 492,404 534,420 576,368 618,388 660,344 692,356"
            fill="none" stroke="#7cc4ff" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round"
          />
          <circle cx="576" cy="368" r="5.5" fill="#ffffff" />
          <circle cx="692" cy="356" r="5.5" fill="#ffffff" />
        </g>

        <g class="login__art-card login__art-card--c">
          <rect x="470" y="120" width="240" height="130" rx="16" fill="url(#cardFill)" stroke="#ffffff" stroke-opacity="0.20" />
          <rect x="500" y="150" width="120" height="10" rx="5" fill="#ffffff" fill-opacity="0.42" />
          <rect x="500" y="176" width="176" height="8" rx="4" fill="#ffffff" fill-opacity="0.22" />
          <rect x="500" y="196" width="150" height="8" rx="4" fill="#ffffff" fill-opacity="0.22" />
          <circle cx="668" cy="206" r="16" fill="none" stroke="#16a34a" stroke-width="3" stroke-opacity="0.9" />
          <polyline points="661,206 666,212 676,199" fill="none" stroke="#16a34a" stroke-width="3"
                    stroke-linecap="round" stroke-linejoin="round" />
        </g>
      </svg>

      <div class="login__brand-content">
        <div class="login__logo">
          <el-icon :size="26"><Reading /></el-icon>
          <span class="login__logo-text">智考在线</span>
        </div>
        <h1 class="login__slogan">智能组卷 · 在线考试 · 学情分析</h1>
        <p class="login__sub">
          一体化在线考试平台。题库、组卷、考试、阅卷、成绩与学情分析在同一条链路上，
          并由 AI 助手用自然语言查询系统数据。
        </p>

        <!--
          这里刻意不做「四张图标卡片」的卖点列表。
          登录页的职责只有一个：让人尽快登录。把产品能力堆在左侧，
          既抢走表单的注意力，也是一眼能看出模板感的写法。
          能力范围压成一行 muted 文字，够交代定位就行。
        -->
        <p class="login__scope">题库 · 组卷 · 在线考试 · 阅卷 · 成绩 · 学情分析</p>
      </div>
    </section>

    <!-- ==================== 右侧：登录表单 ==================== -->
    <section class="login__panel">
      <div class="login__form-wrap">
        <header class="login__form-head">
          <h2 class="login__form-title">欢迎回来</h2>
          <p class="login__form-sub">请使用系统账号登录</p>
        </header>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          @submit.prevent="handleLogin"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="用户名"
              :prefix-icon="User"
              autocomplete="username"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              :prefix-icon="Lock"
              show-password
              autocomplete="current-password"
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login__submit"
            @click="handleLogin"
          >
            {{ loading ? '登录中…' : '登 录' }}
          </el-button>
        </el-form>

        <p class="login__help">请使用管理员分配的账号。忘记密码或尚无账号，请联系系统管理员。</p>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

/**
 * 登录页（三期重做）。
 *
 * <p>一期只有一张 360px 的居中卡片，既不体现产品定位，也不像能交付的东西。
 * 现在改成左右分栏：左侧用内联 SVG 表达「考试 + 数据 + AI」的产品气质，
 * 右侧专注系统账号登录，与实际部署的账号策略一致。
 *
 * <p><b>为什么背景用 SVG 而不是位图：</b>矢量在 4K 与 Retina 下都锐利、
 * 体积接近 0、不产生额外请求（登录页首屏不会因背景图而抖动），
 * 而且颜色可以直接取品牌色梯度，换主题不会出现「背景还是蓝的、按钮已经绿了」。
 * 构图刻意抽象、不含任何文字，避免出现配图里常见的乱码字。
 */
const router = useRouter()
const userStore = useUserStore()

// 账号由部署管理员创建；不预填或公开共享口令。
const form = ref({ username: '', password: '' })
const loading = ref(false)
const formRef = ref()

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  loading.value = true
  try {
    await userStore.login(form.value)
    ElMessage.success('登录成功')
    await router.push('/')
  } catch (error) {
    // The request interceptor already reports authentication and network errors.
    // A rejected login must not escape into Vue's global component error handler.
    return
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(420px, 0.85fr);
  height: 100vh;
  background: var(--zk-bg-card);
}

/* ==================== 左侧品牌区 ==================== */
.login__brand {
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: var(--zk-space-7);
  color: var(--zk-text-inverse);
}

.login__art {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
}

/* 卡片轮廓缓慢浮动：动的是 transform，走 GPU 合成层，不影响左侧文字渲染 */
.login__art-card {
  transform-box: fill-box;
  transform-origin: center;
  animation: login-float 9s ease-in-out infinite;
}

.login__art-card--b {
  animation-duration: 11s;
  animation-delay: -3s;
}

.login__art-card--c {
  animation-duration: 13s;
  animation-delay: -6s;
}

@keyframes login-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-14px); }
}

.login__brand-content {
  position: relative;
  z-index: 1;
  max-width: 520px;
}

.login__logo {
  display: flex;
  align-items: center;
  gap: var(--zk-space-2);
  margin-bottom: var(--zk-space-6);
}

.login__logo-text {
  font-size: var(--zk-font-lg);
  font-weight: 600;
  letter-spacing: 1px;
}

.login__slogan {
  font-size: var(--zk-font-xxl);
  font-weight: 600;
  line-height: 1.35;
  margin-bottom: var(--zk-space-3);
}

.login__sub {
  font-size: var(--zk-font-base);
  line-height: 1.9;
  color: rgba(255, 255, 255, 0.72);
  margin-bottom: var(--zk-space-6);
}

/* 能力范围：一行 muted 文字，不做图标卡片堆 */
.login__scope {
  font-size: var(--zk-font-sm);
  letter-spacing: 0.06em;
  color: rgba(255, 255, 255, 0.55);
}

/* ==================== 右侧表单区 ==================== */
.login__panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--zk-space-6) var(--zk-space-7);
  overflow-y: auto;
}

.login__form-wrap {
  width: 100%;
  max-width: 400px;
}

.login__form-head {
  margin-bottom: var(--zk-space-6);
}

.login__form-title {
  font-size: var(--zk-font-xl);
  font-weight: 600;
  color: var(--zk-text-primary);
}

.login__form-sub {
  margin-top: var(--zk-space-1);
  font-size: var(--zk-font-base);
  color: var(--zk-text-secondary);
}

.login__submit {
  width: 100%;
  margin-top: var(--zk-space-2);
  letter-spacing: 2px;
}

.login__help {
  margin-top: var(--zk-space-6);
  padding-top: var(--zk-space-4);
  border-top: 1px dashed var(--zk-border-strong);
  font-size: var(--zk-font-xs);
  color: var(--zk-text-secondary);
  line-height: 1.7;
}

/* ==================== 响应式 ==================== */
/* 窄屏隐藏左侧品牌区，只保留表单；氛围由表单页头承担，不至于「什么都没有」 */
@media (max-width: 1024px) {
  .login {
    grid-template-columns: 1fr;
  }

  .login__brand {
    display: none;
  }

  .login__panel {
    padding: var(--zk-space-5) var(--zk-space-5);
    background:
      radial-gradient(120% 60% at 50% 0%, var(--zk-primary-light) 0%, transparent 70%),
      var(--zk-bg-card);
  }
}
</style>
