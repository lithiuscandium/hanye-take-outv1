"use strict";const t=require("../utils/http.js");exports.getUserInfoAPI=e=>t.http({url:`/user/user/${e}`,method:"GET"}),exports.updateUserAPI=e=>t.http({url:"/user/user",method:"PUT",data:e});
