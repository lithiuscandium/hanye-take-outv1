import { useUserStore } from '@/stores/modules/user'

// 请求基地址
let baseURL = 'http://localhost:8081'
// #ifdef H5
baseURL = `${window.location.protocol}//${window.location.hostname}:8081`
// #endif

// 拦截器配置
const httpInterceptor = {
  // 拦截前触发
  invoke(options: UniApp.RequestOptions) {
    // 1. 非 http 开头需拼接地址
    if (!options.url.startsWith('http')) {
      options.url = baseURL + options.url
    }
    // 2. 请求超时
    options.timeout = 10000
    // 3. 添加小程序端请求头标识
    options.header = {
      'source-client': 'miniapp',
      ...options.header,
    }
    // 4. 添加 token 请求头标识
    const userStore = useUserStore()
    const token = userStore.profile?.token
    console.log('token', token)
    if (token) {
      options.header.Authorization = token
    }
  },
}

// 拦截 request 请求
uni.addInterceptor('request', httpInterceptor)
// 拦截 uploadFile 文件上传
// uni.addInterceptor('uploadFile', httpInterceptor)

// 定义泛型接口
interface Data<T> {
  code: number
  msg: string
  data: T
}
// 相比axios，uniapp对ts不友好，因此自己封装函数升级request，实现响应拦截器的功能
// 直观体现： wx.request({}) -> async await promise对象
export const http = <T>(options: UniApp.RequestOptions) => {
  return new Promise<Data<T>>((resolve, reject) => {
    uni.request({
      ...options,
      // 响应成功
      success(res) {
        console.log('响应  ', res)
        if (res.statusCode >= 200 && res.statusCode < 300) {
          // 获取数据成功, 调用resolve，返回的是res.data而不是res，能省掉一层嵌套
          // console.log('请求成功', res)
          resolve(res.data as Data<T>) // 类型断言
        } else if (res.statusCode === 401) {
          // 401错误, 说明token过期, 调用reject,
          // 清理用户信息
          const userStore = useUserStore()
          userStore.clearProfile()
          // 记录登录后回跳页面（避免总是回到“我的”页）
          const pages = getCurrentPages()
          const currentPage = pages[pages.length - 1] as { route?: string } | undefined
          const currentRoute = currentPage?.route ? `/${currentPage.route}` : ''
          if (currentRoute && currentRoute !== '/pages/login/login') {
            uni.setStorageSync('login_redirect_url', currentRoute)
          }
          // 跳转到登录页
          uni.reLaunch({ url: '/pages/login/login' })
          reject(res)
        } else {
          // 通用错误, 调用reject, 轻量提示框
          uni.showToast({
            title: (res.data as Data<T>).msg || '请求失败',
            icon: 'none',
          })
        }
      },
      // 响应失败
      fail(err) {
        // 网络错误, 调用reject
        uni.showToast({
          title: '网络不行，换个试试？',
          icon: 'none',
          // mask: true,
        })
        reject(err)
      },
    })
  })
}
