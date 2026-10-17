<template>
  <div
    class="hero-carousel"
    role="region"
    aria-label="运营位轮播"
    @mouseenter="pause"
    @mouseleave="play"
  >
    <div
      v-for="(item, i) in list"
      :key="item.id ?? `banner-${i}`"
      class="slide"
      :class="{ 'is-on': i === cur }"
      :style="slideStyle(item, i)"
      :aria-hidden="String(i !== cur)"
    >
      <span class="deco" aria-hidden="true" />
      <span class="deco2" aria-hidden="true" />
      <div class="txt">
        <span v-if="item.kicker" class="kicker">{{ item.kicker }}</span>
        <h2>
          <template v-for="(line, li) in titleLines(item.title)" :key="li">
            <br v-if="li > 0" />{{ line }}
          </template>
        </h2>
        <p v-if="item.subtitle">{{ item.subtitle }}</p>
        <button v-if="item.cta" class="cta" type="button" @click="onCta(item)">
          {{ item.cta }}
        </button>
      </div>
    </div>

    <!-- 左右箭头：hover 变实底、放大 -->
    <button class="car-arrow car-prev" type="button" aria-label="上一帧" @click="prev">‹</button>
    <button class="car-arrow car-next" type="button" aria-label="下一帧" @click="next">›</button>

    <!-- 圆点：固定在右下角（right 44px），CTA 在左侧文字区（max-width 60%），两者永不同排重叠 -->
    <div class="dots">
      <i
        v-for="(item, i) in list"
        :key="`dot-${item.id ?? i}`"
        :class="{ 'is-on': i === cur }"
        :aria-label="`切换到第 ${i + 1} 帧`"
        @click="go(i)"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

/**
 * 门户轮播广告位（五期首页）。
 *
 * 数据来自 /portal/home 的 banners；后端未上线时由 StudentHome 注入
 * 与视觉稿一致的 3 帧默认文案，组件本身不关心来源。
 * theme 字段（s1/s2/s3）对应视觉稿三套渐变底；缺省或未知值按帧序轮换。
 */
const props = defineProps({
  items: { type: Array, default: () => [] }
})

const AUTOPLAY_MS = 5000

const THEMES = {
  s1: 'linear-gradient(120deg, #123C8C, #3E6FE0 55%, #6FA0FF)',
  s2: 'linear-gradient(120deg, #0B6E4B, #0E9F6E 55%, #5BD3A5)',
  s3: 'linear-gradient(120deg, #7A2E4E, #D6455D 60%, #FF8E6E)'
}
const THEME_KEYS = Object.keys(THEMES)

const router = useRouter()
const cur = ref(0)
let timer = null

const list = computed(() => (Array.isArray(props.items) ? props.items : []))

function slideStyle(item, index) {
  const key = THEMES[item?.theme] ? item.theme : THEME_KEYS[index % THEME_KEYS.length]
  return { background: THEMES[key] }
}

/** 标题支持 \n / <br> 断行（视觉稿的衬线大标题都是两行） */
function titleLines(title) {
  return String(title || '')
    .split(/\n|<br\s*\/?>/i)
    .filter(Boolean)
}

function go(i) {
  const n = list.value.length
  if (!n) return
  cur.value = ((i % n) + n) % n
}
function next() {
  go(cur.value + 1)
}
function prev() {
  go(cur.value - 1)
}

function play() {
  stop()
  if (list.value.length > 1) timer = setInterval(next, AUTOPLAY_MS)
}
function stop() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}
function pause() {
  stop()
}

function onCta(item) {
  if (item?.link) {
    if (/^https?:\/\//.test(item.link)) window.open(item.link, '_blank')
    else router.push(item.link)
    return
  }
  ElMessage.info('活动详情建设中，敬请期待')
}

watch(
  () => list.value.length,
  () => {
    cur.value = 0
    play()
  }
)

onMounted(play)
onBeforeUnmount(stop)
</script>

<style scoped>
.hero-carousel {
  position: relative;
  border-radius: var(--zk-portal-radius-lg);
  overflow: hidden;
  box-shadow: var(--zk-portal-shadow-m);
  height: 250px;
}

.slide {
  position: absolute;
  inset: 0;
  opacity: 0;
  transition: opacity 0.6s ease;
  display: flex;
  align-items: center;
  padding: 0 44px;
  pointer-events: none;
}
.slide.is-on {
  opacity: 1;
  pointer-events: auto;
}

.slide .txt {
  position: relative;
  z-index: 2;
  max-width: 60%;
}
.slide .kicker {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 2px;
  color: #fff;
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid rgba(255, 255, 255, 0.4);
  padding: 4px 12px;
  border-radius: 999px;
  backdrop-filter: blur(4px);
}
.slide h2 {
  font-family: var(--zk-font-display);
  font-size: 32px;
  font-weight: 900;
  color: #fff;
  line-height: 1.35;
  margin: 14px 0 6px;
  text-shadow: 0 2px 14px rgba(0, 0, 0, 0.18);
}
.slide p {
  color: rgba(255, 255, 255, 0.92);
  font-size: 14px;
  font-weight: 500;
}
.slide .cta {
  margin-top: 16px;
  background: #fff;
  color: var(--zk-portal-ink);
  padding: 10px 22px;
  border-radius: 999px;
  font-weight: 900;
  font-size: 14px;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.18);
  transition: transform 0.16s ease;
  cursor: pointer;
  border: none;
}
.slide .cta:hover {
  transform: translateY(-2px);
}

/* 装饰圆角块：与视觉稿同款的「运营感」 */
.slide .deco {
  position: absolute;
  right: -40px;
  bottom: -60px;
  width: 300px;
  height: 300px;
  border-radius: 50%;
  border: 44px solid rgba(255, 255, 255, 0.14);
  z-index: 1;
}
.slide .deco2 {
  position: absolute;
  right: 120px;
  top: 30px;
  width: 70px;
  height: 70px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.16);
  transform: rotate(18deg);
  z-index: 1;
}

.dots {
  position: absolute;
  right: 44px;
  bottom: 20px;
  display: flex;
  gap: 7px;
  z-index: 3;
}
.dots i {
  width: 20px;
  height: 5px;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.4);
  cursor: pointer;
  transition: all 0.25s ease;
}
.dots i.is-on {
  width: 34px;
  background: #fff;
}

.car-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.85);
  display: grid;
  place-items: center;
  z-index: 3;
  font-weight: 900;
  font-size: 20px;
  line-height: 1;
  color: var(--zk-portal-ink);
  box-shadow: var(--zk-portal-shadow-s);
  transition: all 0.15s ease;
  cursor: pointer;
  border: none;
}
.car-arrow:hover {
  background: #fff;
  transform: translateY(-50%) scale(1.08);
}
.car-prev {
  left: 14px;
}
.car-next {
  right: 14px;
}

/* ≤860px：隐藏箭头与圆点（视觉稿约定：窄屏轮播只自动播） */
@media (max-width: 860px) {
  .hero-carousel {
    height: 210px;
  }
  .slide {
    padding: 0 26px;
  }
  .slide h2 {
    font-size: 24px;
  }
  .dots,
  .car-arrow {
    display: none;
  }
}
</style>
