"use strict";
const common_vendor = require("../../common/vendor.js");
const common_assets = require("../../common/assets.js");
const api_login = require("../../api/login.js");
const stores_modules_user = require("../../stores/modules/user.js");
require("../../utils/http.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "login",
  setup(__props) {
    let code = "";
    common_vendor.onLoad(async () => {
      const res = await common_vendor.wx$1.login();
      code = res.code;
    });
    const login = async () => {
      try {
        console.log("login");
        if (!code) {
          common_vendor.index.showToast({
            title: "登录凭证为空，请重试",
            icon: "none"
          });
          return;
        }
        const res = await api_login.loginAPI(code);
        console.log(res);
        if (res.code !== 0 || !res.data || !res.data.token || !res.data.id) {
          common_vendor.index.showToast({
            title: res.msg || "登录失败",
            icon: "none"
          });
          return;
        }
        loginSuccess(res.data);
      } catch (e) {
        common_vendor.index.showToast({
          title: "登录异常，请稍后再试",
          icon: "none"
        });
      }
    };
    const loginSuccess = (profile) => {
      const userStore = stores_modules_user.useUserStore();
      userStore.setProfile(profile);
      common_vendor.index.showToast({ icon: "success", title: "登录成功" });
      setTimeout(() => {
        const redirectUrl = common_vendor.index.getStorageSync("login_redirect_url");
        if (redirectUrl && redirectUrl !== "/pages/login/login") {
          common_vendor.index.removeStorageSync("login_redirect_url");
          if (redirectUrl === "/pages/index/index" || redirectUrl === "/pages/my/my") {
            common_vendor.index.switchTab({ url: redirectUrl });
          } else {
            common_vendor.index.reLaunch({ url: redirectUrl });
          }
          return;
        }
        common_vendor.index.switchTab({ url: "/pages/my/my" });
      }, 500);
    };
    const tips = async () => {
      common_vendor.index.showToast({
        title: "司辰，直接微信快捷登录就好哦~",
        icon: "none"
      });
    };
    return (_ctx, _cache) => {
      return {
        a: common_assets._imports_0,
        b: common_vendor.o(login),
        c: common_vendor.o(tips)
      };
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-cdfe2409"], ["__file", "D:/new1/hanye-take-out/hanye-take-out-uniapp/src/pages/login/login.vue"]]);
wx.createPage(MiniProgramPage);
