"use strict";const t=require("../utils/http.js");exports.loginAPI=e=>t.http({method:"POST",url:"/user/user/login",data:{code:e}});
