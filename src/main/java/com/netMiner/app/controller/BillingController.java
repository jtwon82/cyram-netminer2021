package com.netMiner.app.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.ModelAndView;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.netMiner.app.config.Constant;
import com.netMiner.app.config.SendEmail;
import com.netMiner.app.model.service.BillingService;
import com.netMiner.app.model.service.MemberService;
import com.netMiner.app.model.vo.BillingVo;
import com.netMiner.app.model.vo.MemberVo;
import com.netMiner.app.util.Base64Util;
import com.netMiner.app.util.StringUtils2;

@Controller
public class BillingController extends HttpServlet {

	private static final Logger logger = LoggerFactory.getLogger(BillingController.class);
	
	@Autowired
	private BillingService billingService;
	
	@Autowired
	private MemberService memberService;
	
	@Autowired
	private SendEmail sendEmail;
	
	
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final RestTemplate restTemplate = new RestTemplate();
	/*page Move Billing*/
	@RequestMapping(value="pricing", method=RequestMethod.GET) 
	public String pricing (Model mv,HttpSession session, HttpServletRequest request, HttpServletResponse response) {
		String language = (String) session.getAttribute("language");
		MemberVo member = (MemberVo) session.getAttribute("memberVo");
		String type = (String) request.getParameter("type");

		Map<String,Object> param = new HashMap<String,Object>();
		if (language.equals("_EN")) {
			param.put("language","en");
		} else {
			param.put("language","ko");
		}

		//faq ??? ?????? ????????? ?????? ????????? ??????????????? ????????? ??? select 
		List<Map<String,Object>> faqList = billingService.selectFaqList(param);
		logger.info("faqList - {}", faqList.toString());
		mv.addAttribute("faqList", faqList);
		// ????????? ??????????????? ?????? ???????????? ???????????? ?????? ?????? 
		if (member == null ) {
			String path = "homePage"+ language;
			return path+"/pricing";
		}

		String userId = member.getUserId();
		boolean checkDate = false;

		param = new HashMap<String,Object>();
		param.put("userId", userId);
		Map<String, Object> result = billingService.selectSubscript(param);
		int diffDays = 0;
		logger.info("result- {}",result);
		if (result != null) {
			diffDays = Integer.parseInt(result.get("DIFF_DAY").toString());
		}
		
		/*
		Date nowDate = new Date(System.currentTimeMillis());
		 SimpleDateFormat dateFormat = new 
	                SimpleDateFormat ("yyyy-MM-dd");
		 if (result != null) {
			 try {
					Date now = dateFormat.parse(dateFormat.format(nowDate));
					Date exitsDate = dateFormat.parse(dateFormat.format(result.get("EXITS_DATE")));
					logger.info("date1 -{}",now.toString());
					logger.info("date2 -{}",exitsDate.toString());
					diffDays = (int) (((exitsDate.getTime() - now.getTime())/1000)/ (24*60*60));					
				} catch (ParseException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		 }
		 */		 
		if (diffDays <= 0) {
			result = null;
		}
		
		//?????? ????????? ???????????? 01 ???????????? ?????? 
		Map<String, Object> checkUserTiralInfo = billingService.checkUserTiralInfo(param);
		logger.info("checkUserTiralInfo- {}",checkUserTiralInfo);
		//??????????????? ?????? ????????? TRAIL ?????? ????????? ??????????????? 
		if (result != null) {
			//Trial ??? ????????? ????????? 
			if (result.get("PLAN_CODE").equals("01")) {
				//?????? ???????????? ????????? Trial ?????????
				member.setPlanType(1);
			} else {
				//?????? ???????????? ????????? Trial ??? ???????????? 
				if (type != null) {
					if (type.indexOf("changePlan") > -1) {
						//?????? ?????? ???????????? 
						member.setPlanType(1);
					} else if (type.indexOf("upgradePlan") > -1) {
						//?????? ????????? ????????????????????? 						
						member.setPlanType(Integer.parseInt((String) result.get("PLAN_CODE")));							
					} else {
						//billing ??? ?????? ????????? user ??? ???????????? 
						member.setPlanType(Integer.parseInt((String) result.get("PLAN_CODE")));
					}
				} else {
					
					member.setPlanType(Integer.parseInt((String) result.get("PLAN_CODE")));
				}
			}
		} else {
				//Trial ??? ???????????? ?????? ?????? ????????????  ??????
			if (checkUserTiralInfo != null) {
				member.setPlanType(-1);
			} else { 
				//??????  ????????? 
				member.setPlanType(0);
			}
		}
		
		logger.info("memberPlanType- {}, language-{}", member.getPlanType(), member.getLanguage());
		session.setAttribute("memberVo",member);
		logger.info(language);
		String path = "homePage"+ language;
		return path+"/pricing";
	}
	
	@RequestMapping(value="billing", method=RequestMethod.GET) 
	public String billing (HttpSession session, Model mv,HttpServletRequest request, HttpServletResponse response) {
		String language = (String) session.getAttribute("language");
		MemberVo member = (MemberVo) session.getAttribute("memberVo");
		if (member == null ) {
			return "redirect:/login";
		}
		String userId = member.getUserId();
		Map<String,Object> param = new HashMap<String,Object>();
		param.put("userId", userId);
		List<Map<String,Object>> billingList = billingService.selectSubscriptAll(param);
		Map<String, Object> nowPlan = billingService.selectSubscript(param);
		
		BillingVo billingVo = new BillingVo().fromMap((HashMap<String, Object>)nowPlan);
		/*
		int diffDays = 0;
		Date nowDate = new Date(System.currentTimeMillis());
		 SimpleDateFormat dateFormat = new 
	                SimpleDateFormat ("yyyy-MM-dd");
		 if (nowPlan != null) {
			 try {
					Date now = dateFormat.parse(dateFormat.format(nowDate));
					Date exitsDate = dateFormat.parse(dateFormat.format(nowPlan.get("EXITS_DATE")));
					logger.info("date1 -{}",now.toString());
					logger.info("date2 -{}",exitsDate.toString());
					diffDays = (int) (((exitsDate.getTime() - now.getTime())/1000)/ (24*60*60));					
				} catch (ParseException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		 }
		 */
		int diffDays = 0;
		if (billingVo != null) {
			diffDays = billingVo.getDiffDay();
			
		}
		 //?????? ?????? 7??? timestamp		 
		 
		if (nowPlan == null && billingList.size() > 0) {
			// ?????? ????????? ?????? ?????? ?????? ????????? ???????????? 
			mv.addAttribute("nowPlan", "none");
			mv.addAttribute("billingList", billingList);
		} else if (nowPlan != null && billingList.size() == 0) {
			// ?????? ????????? ?????????  ?????? ?????? ????????? ???????????? 
			mv.addAttribute("nowPlan", nowPlan);
			mv.addAttribute("diffDays", diffDays);
			mv.addAttribute("billingList", "none");
			billingVo.setDiffDay(diffDays);
			session.setAttribute("billingOld", billingVo);
		} else if (nowPlan != null && billingList.size() > 0){
			// ?????? ?????? ?????? ?????? 
			mv.addAttribute("nowPlan", nowPlan);
			mv.addAttribute("diffDays", diffDays);
			mv.addAttribute("billingList", billingList);
			billingVo.setDiffDay(diffDays);
			session.setAttribute("billingOld", billingVo);
		} else {
			//?????? ????????? ?????? ?????? ?????? ???????????? ???????????? 
			mv.addAttribute("nowPlan","none");
			mv.addAttribute("billingList", "none");
			
		}
		String path = "homePage"+ language;
		return path + "/billing";
	}
	
	@RequestMapping(value="goSubscribe", method=RequestMethod.GET) 
	public String subscribe (Model mv,HttpSession session,HttpServletRequest request, HttpServletResponse response) {
		String language = (String) session.getAttribute("language");
		String path = "homePage"+ language;
		String payNo= request.getParameter("payNo");
		String planCode= request.getParameter("planCode");
		String dateType = request.getParameter("dateType") == null ? "year" : request.getParameter("dateType");
		String payType = request.getParameter("payType") == null ? "card" : request.getParameter("payType");
		BillingVo billingVo = (BillingVo) session.getAttribute("billing");
		BillingVo billingOldVo = (BillingVo) session.getAttribute("billingOld");
		MemberVo memberVo = (MemberVo) session.getAttribute("memberVo");
		String timestamp= new SimpleDateFormat("HHmmss").format(new Date());
		int randomNo= ThreadLocalRandom.current().nextInt(1000000, 10000000);
		String pagePath = "";
		logger.info("hellow");
		if (memberVo == null ) {
			return "redirect:/login";
		}
		logger.info("dateType- {}", dateType);
		logger.info("billingOldVo-{}",billingOldVo);
		
		//planCode ??? ???????????? ?????? ??????????????? ???????????? ?????? 
		if (planCode == null && payNo == null) {
			return "redirect:/";
		}
		
		//pricing ?????? ?????? ????????? ??????????????? ?????? billingOldVo ??? ??????????????? ?????? ?????????????????????
		if (billingOldVo == null) {
			String userId = memberVo.getUserId();
			Map<String,Object> param = new HashMap<String,Object>();
			param.put("userId", userId);
			Map<String, Object> nowPlan = null ;
			nowPlan = billingService.selectSubscript(param);
			if (nowPlan != null) {
				billingOldVo = new BillingVo().fromMap((HashMap<String, Object>)nowPlan);
				/*
				int diffDays = 0;
				Date nowDate = new Date(System.currentTimeMillis());
				SimpleDateFormat dateFormat = new 
						SimpleDateFormat ("yyyy-MM-dd");
				try {
					Date now = dateFormat.parse(dateFormat.format(nowDate));
					Date exitsDate = dateFormat.parse(dateFormat.format(nowPlan.get("EXITS_DATE")));

					diffDays = (int) (((exitsDate.getTime() - now.getTime())/1000)/ (24*60*60));					
				} catch (ParseException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				billingOldVo.setDiffDay(diffDays);
				*/
				
			}
		}
		
		if (billingOldVo != null && 
				!billingOldVo.getPLAN_CODE().equals("01")
				&& payNo != null) {
			planCode = billingOldVo.getPLAN_CODE();
		}
		
		HashMap<String , Object> param= new HashMap<String , Object>();
		HashMap<String ,Object> billingMap= null;
		param.put("planCode", planCode);
		billingMap= billingService.selectPlanCode(param);	
		billingVo = new BillingVo().fromMap(billingMap);
		billingVo.setDATE_TYPE(dateType);
		billingVo.setORDER_ID("ORD_"+ new Base64Util().enCodingBase64(String.format("%s%s%s%s", planCode, dateType, timestamp, randomNo)));
		billingVo.setORDER_PNM(billingMap.get("PLAN_NAME").toString());
		billingVo.setCUSTOMER_NAME("CUSTOMER_NAME");
		billingVo.setPAY_TYPE(payType);
		
		//billingOld Vo ??? ?????? ????????? ?????? ????????? ?????? ?????? 01 ????????? ?????? olDvo??? null ????????????	
		if (billingOldVo != null) {
			//?????? ?????? ????????? ????????? ???????????? ?????? billingOldVo ??? null ??? ?????? 
			if (billingOldVo.getPLAN_CODE().equals("01")) {
				billingOldVo = null;
			}
		}
		
		logger.info("billingVoSelectPlanCode - {} ", billingVo.toString());
		if (! planCode.equals("01")) {
			if (language.equals("_EN")) {
				billingVo.setPAY_PLATFORM("paypal");
				if (billingOldVo != null) {
					if (billingOldVo.getDiffDay() >= -7 && billingOldVo.getDiffDay() <= 7) {
						//?????? ?????? ????????? 
						if (dateType.equals("year")) {
							int total = (int) ((billingVo.getPLAN_PER_EN()* 12) - (billingVo.getPLAN_PER_EN()* 12 )* 0.2);
							billingVo.setPAY_PRICE(total);
							billingVo.setPAY_PRICE_VAT(billingVo.getPAY_PRICE()* 100/110);
							billingVo.setVAT(billingVo.getPAY_PRICE()-billingVo.getPAY_PRICE_VAT());
							billingVo.setDiffDay(365 + billingOldVo.getDiffDay());
							billingVo.setDATE_TYPE("year"); 
							billingVo.setType("extensionPlan");
						} else {
							billingVo.setPAY_PRICE( billingVo.getPLAN_PER_EN());
							billingVo.setPAY_PRICE_VAT(billingVo.getPAY_PRICE()* 100/110);
							billingVo.setVAT(billingVo.getPAY_PRICE()-billingVo.getPAY_PRICE()* 100/110);
							billingVo.setDiffDay(30 + billingOldVo.getDiffDay());
							billingVo.setDATE_TYPE("month");
							billingVo.setType("extensionPlan");
						}
						
					} else {
						String payPlatform = (String) billingOldVo.getPAY_PLATFORM(); 
						String payLanguage = payPlatform.equals("toss")?"ko":"en";
						String pageLanguage = language.equals("_EN")?"en":"ko";
						if (!payLanguage.equals(pageLanguage)) {
							 response.setContentType("text/html; charset=UTF-8");
							 String comment= "?????? ?????? ????????? ?????????????????? ????????? ???????????? ????????????.";
							 if (pageLanguage.equals("en")) {
								 comment = "You cannot proceed with the payment in a different way than the previous payment history.";
							 }
							 PrintWriter out;
							try {
								out = response.getWriter();
								out.println("<script>alert('"+comment+"'); location.href='./billing';</script>");
								out.flush();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
						
						if (billingOldVo.getDATE_TYPE().equals("year")) {
							// 1????????? 1??? ??????????????????
							int oldPrice = (int) ((billingOldVo.getPLAN_PER_EN()* 12) - (billingOldVo.getPLAN_PER_EN()* 12 )* 0.2);
							int nowPrice = (int) ((billingVo.getPLAN_PER_EN()* 12) - (billingVo.getPLAN_PER_EN()* 12 )* 0.2);
//							int result = (nowPrice/365*(billingOldVo.getDiffDay())) - oldPrice - (oldPrice * ((365 - billingOldVo.getDiffDay()) /365));
							double nowPricePerDay= Math.round(((double)nowPrice/365)*100) / 100.0;
							double oldPricePerDay = Math.round(((double)oldPrice/365)*100)/ 100.0;
							int diffDay= billingOldVo.getStartForDiffDay();
							double firstResult = (nowPricePerDay*(365-diffDay)) + (oldPricePerDay*diffDay) - oldPrice;;
							int result = (int) Math.round(firstResult);
								
							billingVo.setPAY_PRICE(result);
							billingVo.setPAY_PRICE_VAT(billingVo.getPAY_PRICE()* 100/110);
							billingVo.setVAT(billingVo.getPAY_PRICE()-billingVo.getPAY_PRICE()* 100/110);
							billingVo.setDiffDay(billingOldVo.getDiffDay());
							billingVo.setEXITS_DATE(billingOldVo.getEXITS_DATE());
							billingVo.setDATE_TYPE("year");
							billingVo.setType("upgradePlan");
							logger.info("billingVo year- {}", billingVo.toString());
						} else {
							// ???????????? ?????? ?????????????????? 
							int oldPrice = billingOldVo.getPLAN_PER_EN();
							int nowPrice = billingVo.getPLAN_PER_EN();
							//int result = (nowPrice/30*(billingOldVo.getDiffDay())) - oldPrice - (oldPrice * ((30 - billingOldVo.getDiffDay()) /30));
							double nowPricePerDay= Math.round(((double)nowPrice/30)*100) / 100.0;	// ????????? ????????????
							double oldPricePerDay = Math.round(((double)oldPrice/30)*100)/ 100.0;	// ?????? ????????????
							//double firstResultOld = (billingOldVo.getDiffDay() * nowPricePerDay) - (oldPrice - (((30 - billingOldVo.getDiffDay()) * oldPricePerDay)));
							int diffDay= billingOldVo.getStartForDiffDay();
							double firstResult = (nowPricePerDay*(30-diffDay)) + (oldPricePerDay*diffDay) - oldPrice;
							int result = (int) Math.round(firstResult);
							
							billingVo.setPAY_PRICE(result);
							billingVo.setPAY_PRICE_VAT(billingVo.getPAY_PRICE()* 100/110);
							billingVo.setVAT(billingVo.getPAY_PRICE()-billingVo.getPAY_PRICE()* 100/110);
							billingVo.setDiffDay(billingOldVo.getDiffDay());
							billingVo.setEXITS_DATE(billingOldVo.getEXITS_DATE());
							billingVo.setDATE_TYPE("month");
							billingVo.setType("upgradePlan");
							logger.info("billingVo month - {}", billingVo.toString());
						}					
					}
				} else {
					if (dateType.equals("year")) {
						int total = (int) ((billingVo.getPLAN_PER_EN()* 12) - (billingVo.getPLAN_PER_EN()* 12 )* 0.2);
						billingVo.setPAY_PRICE(total);
						billingVo.setPAY_PRICE_VAT(billingVo.getPAY_PRICE()* 100/110);
						billingVo.setVAT(billingVo.getPAY_PRICE()-billingVo.getPAY_PRICE()* 100/110);
						billingVo.setType("none");
					} else {
						billingVo.setPAY_PRICE( billingVo.getPLAN_PER_EN());
						billingVo.setPAY_PRICE_VAT(billingVo.getPAY_PRICE()* 100/110);
						billingVo.setVAT(billingVo.getPAY_PRICE()-billingVo.getPAY_PRICE()* 100/110);
						billingVo.setType("none");
					}
				}
			} else {
				billingVo.setPAY_PLATFORM("toss");
				if (billingOldVo != null) {
					if (billingOldVo.getDiffDay()  >= -7 && billingOldVo.getDiffDay() <= 7) {
						//?????? ?????? ????????? 
						if (dateType.equals("year")) {
							int total = (int) ((billingVo.getPLAN_PER_KO()* 12) - (billingVo.getPLAN_PER_KO()* 12 )* 0.2);
							billingVo.setPAY_PRICE(total);
							billingVo.setPAY_PRICE_VAT(billingVo.getPAY_PRICE()* 100/110);
							billingVo.setVAT(billingVo.getPAY_PRICE()-billingVo.getPAY_PRICE()* 100/110);
							billingVo.setDiffDay(365 + billingOldVo.getDiffDay());
							billingVo.setDATE_TYPE("year"); 
							billingVo.setType("extensionPlan");
						} else {
							billingVo.setPAY_PRICE( billingVo.getPLAN_PER_KO());
							billingVo.setPAY_PRICE_VAT(billingVo.getPAY_PRICE()* 100/110);
							billingVo.setVAT(billingVo.getPAY_PRICE()-billingVo.getPAY_PRICE()* 100/110);
							billingVo.setDiffDay(30 + billingOldVo.getDiffDay());
							billingVo.setDATE_TYPE("month");
							billingVo.setType("extensionPlan");
						}
						
					} else {
						String payPlatform = (String) billingOldVo.getPAY_PLATFORM(); 
						String payLanguage = payPlatform.equals("toss")?"ko":"en";
						String pageLanguage = language.equals("_EN")?"en":"ko";
						if (!payLanguage.equals(pageLanguage)) {
							 response.setContentType("text/html; charset=UTF-8");
							 String comment= "?????? ?????? ????????? ?????????????????? ????????? ???????????? ????????????.";
							 if (pageLanguage.equals("en")) {
								 comment = "You cannot proceed with the payment in a different way than the previous payment history.";
							 }
							 PrintWriter out;
							try {
								out = response.getWriter();
								out.println("<script>alert('"+comment+"'); location.href='./billing';</script>");
								out.flush();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
						if (billingOldVo.getDATE_TYPE().equals("year")) {
							// 1????????? 1??? ??????????????????
							int oldPrice = (int) ((billingOldVo.getPLAN_PER_KO()* 12) - (billingOldVo.getPLAN_PER_KO()* 12) *0.2);
							int nowPrice = (int) ((billingVo.getPLAN_PER_KO()* 12) - (billingVo.getPLAN_PER_KO()* 12) *0.2);
							
//							int resultOld = ((billingOldVo.getDiffDay())*((int)Math.round(Math.round((nowPrice/365))/10.0) * 10))
//									-(oldPrice-((365 - billingOldVo.getDiffDay()) * ((int)Math.round(Math.round((oldPrice/365))/10.0) * 10)));
//							
//							double oldPricePerDay= (int)Math.round(Math.round((oldPrice/365))/10.0) * 10;
//							int diffDay= billingOldVo.getStartForDiffDay();
//							double firstResult = nowPrice - (oldPrice - (diffDay * oldPricePerDay)) ;
//							int result = (int) Math.round(firstResult);

							int nowPricePerDay = (int)Math.round((nowPrice/365)/10.0)*10; // ????????? ????????????
							int oldPricePerDay = (int)Math.round((oldPrice/365)/10.0)*10; // ?????? ????????????
							int diffDay= billingOldVo.getStartForDiffDay();
							int result = (nowPricePerDay*(365-diffDay)) + (oldPricePerDay*diffDay) - oldPrice;
							
							billingVo.setPAY_PRICE(result);
							billingVo.setPAY_PRICE_VAT(billingVo.getPAY_PRICE()* 100/110);
							billingVo.setVAT(billingVo.getPAY_PRICE()-billingVo.getPAY_PRICE()* 100/110);
							billingVo.setDiffDay(billingOldVo.getDiffDay());
							billingVo.setEXITS_DATE(billingOldVo.getEXITS_DATE());
							billingVo.setDATE_TYPE("year");
							billingVo.setType("upgradePlan");
							logger.info("billingVo year- {}", billingVo.toString());
						} else {
							// ???????????? ?????? ?????????????????? 
							int oldPrice = billingOldVo.getPLAN_PER_KO();
							int nowPrice = billingVo.getPLAN_PER_KO();

//							int resultOld = ((billingOldVo.getDiffDay())*((int)Math.round(Math.round((nowPrice/30))/10.0) * 10))
//									-(oldPrice-((30 - billingOldVo.getDiffDay()) * ((int)Math.round(Math.round((oldPrice/30))/10.0) * 10)));
//
//							double oldPricePerDay= (int)Math.round(Math.round((oldPrice/365))/10.0) * 10;
//							int diffDay= billingOldVo.getStartForDiffDay();
//							double firstResult = nowPrice - (oldPrice - (diffDay * oldPricePerDay)) ;
//							int result = (int) Math.round(firstResult);

							int nowPricePerDay = (int)Math.round((nowPrice/30)/10.0)*10; // ????????? ????????????
							int oldPricePerDay = (int)Math.round((oldPrice/30)/10.0)*10; // ?????? ????????????
							int diffDay= billingOldVo.getStartForDiffDay();
							int result = (nowPricePerDay*(30-diffDay)) + (oldPricePerDay*diffDay) - oldPrice;
							
							billingVo.setPAY_PRICE(result);
							billingVo.setPAY_PRICE_VAT(billingVo.getPAY_PRICE()* 100/110);
							billingVo.setVAT(billingVo.getPAY_PRICE()-billingVo.getPAY_PRICE()* 100/110);
							billingVo.setDiffDay(billingOldVo.getDiffDay());
							billingVo.setEXITS_DATE(billingOldVo.getEXITS_DATE());
							billingVo.setDATE_TYPE("month");
							billingVo.setType("upgradePlan");
							logger.info("billingVo month - {}", billingVo.toString());
						}					
					}
				} else {
					if (dateType.equals("year")) {
						int total = (int) ((billingVo.getPLAN_PER_KO()* 12) - (billingVo.getPLAN_PER_KO()* 12 )* 0.2);
						billingVo.setPAY_PRICE(total);
						billingVo.setPAY_PRICE_VAT(billingVo.getPAY_PRICE()* 100/110);
						billingVo.setVAT(billingVo.getPAY_PRICE()-billingVo.getPAY_PRICE()* 100/110);
						billingVo.setType("none");
					} else {
						billingVo.setPAY_PRICE( billingVo.getPLAN_PER_KO());
						billingVo.setPAY_PRICE_VAT(billingVo.getPAY_PRICE()* 100/110);
						billingVo.setVAT(billingVo.getPAY_PRICE()-billingVo.getPAY_PRICE()* 100/110);
						billingVo.setType("none");
					}
				}
				
			}
		}
		
		
		if (planCode.equals("01")) {
			billingVo.setUSER_ID(memberVo.getUserId());
			billingVo.setDATE_TYPE("month");
			billingVo.setPLAN_NAME("TRIAL");
			billingService.insertSubscript(billingVo);
			session.setAttribute("billing",billingVo);
			pagePath = "redirect:/goSubscribeCompleteTrial";
		} else {
			
			if (payNo != null) {
				// payNo ??? ?????? ???????????? ?????? ????????? ?????? 
				param.put("payNo", payNo);
				param.put("userId", memberVo.getUserId());
				Map<String ,Object> result = billingService.selectSubscript(param);
				logger.info("result -{}", result);
				mv.addAttribute("payState", result);
			}
			
			pagePath = path+"/subscribe";
		}
		session.setAttribute("billing",billingVo);
		logger.info("billing -{}", billingVo.toString());
		//mv.setViewName(path+"/subscribe");
		
		return pagePath;
	}
	
	
	/*????????? ?????? ????????? ????????? ?????? ?????? */
	@RequestMapping(value="payment", method= {RequestMethod.GET, RequestMethod.POST})
	public String payment ( HttpSession session,HttpServletRequest request, HttpServletResponse response
			, BillingVo form) throws Exception {
		String language = (String) session.getAttribute("language");
		MemberVo memberVo = (MemberVo) session.getAttribute("memberVo");
		BillingVo billingVo = (BillingVo) session.getAttribute("billing");
		String payNo =  request.getParameter("payNo");
		
		logger.info("form {}", form);
		logger.info("memberVo {}", memberVo);
		logger.info("billingVo {}", billingVo);
		
		if(form == null || memberVo == null || billingVo == null) {
			StringUtils2.script(response, language, "????????? ???????????????.", "The wrong approach", "./");
			return "redirect:/";
		}
		else {
			if (form.getOrderId().equals(billingVo.getORDER_ID()) && payNo == null) {
				billingVo.setUSER_ID(memberVo.getUserId());
				billingVo.setOrderId(form.getOrderId());
				billingVo.setPaymentKey(form.getPaymentKey());
				billingVo.setAmount(form.getAmount());
				
				try {
					HttpHeaders headers= new HttpHeaders();
					headers.set("Authorization", "Basic " + Base64.getEncoder().encodeToString((Constant.TOSS_SECRET_KEY + ":").getBytes()));
					headers.setContentType(MediaType.APPLICATION_JSON);
					
					Map<String, String> payloadMap= new HashMap<>();
					payloadMap.put("orderId", form.getOrderId());
					payloadMap.put("amount", String.valueOf(form.getAmount()));
					
					HttpEntity<String> request1= new HttpEntity<>(objectMapper.writeValueAsString(payloadMap),
							headers);

					ResponseEntity<JsonNode> responseEntity= restTemplate.postForEntity(
							"https://api.tosspayments.com/v1/payments/" + form.getPaymentKey(), request1,
							JsonNode.class);
					logger.info("responseEntity {}", responseEntity);
					
					if (responseEntity!=null && responseEntity.getStatusCode() == HttpStatus.OK) {
						JsonNode obj = responseEntity.getBody();
						String payType= obj.get("method").toString();
						if (payType.indexOf("????????????") > -1) {
							payType = "bank";
							
							JsonNode transfer= obj.get("transfer");
							logger.info("transfer {}", transfer);
							if (transfer==null) {
//								StringUtils2.script(response, language, "????????? ??????????????????.", "Sorry fail.", "./");
//								return "redirect:/";
							}
						} else {
							payType = "card";
						}

						billingVo.setPAY_TYPE(payType);

						billingService.insertSubscript(billingVo);
						logger.info("insert succ billingVo {}", billingVo);
						
						session.setAttribute("billingVo", billingVo);
						
						return "redirect:/goSubscribeComplete";
					} else {
						return "redirect:/pricing";
					}
				} catch (Exception e) {
					return "redirect:/fail?e=";
				}
			} else {
				return "redirect:/";
			}
		}
	}

	@RequestMapping(value="payment_paypal", method= {RequestMethod.GET, RequestMethod.POST})
	public String payment_paypal ( ModelAndView mv, HttpSession session,HttpServletRequest request, HttpServletResponse response
			, BillingVo form) {
		String language = (String) session.getAttribute("language");
		MemberVo memberVo = (MemberVo) session.getAttribute("memberVo");
		BillingVo billingVo = (BillingVo) session.getAttribute("billing");
		String payNo =  request.getParameter("payNo");
		String path = "homePage"+ language;

		logger.info("form {}", form);
		logger.info("billingVo {}", billingVo);
		if (form.getPayerID() != null) {
			billingVo.setUSER_ID(memberVo.getUserId());
			billingVo.setOrderId(billingVo.getOrderId());
			billingVo.setPaymentKey(form.getPayerID());
			billingVo.setAmount(billingVo.getPAY_PRICE());
			
			billingService.insertSubscript(billingVo);
			logger.info("insert succ PayPal billingVo {}", billingVo);
			session.setAttribute("billingVo", billingVo);
			return "redirect:/payment_paypal_final";
		}  else {
			return "redirect:/";
		}
	}
	@RequestMapping(value="payment_paypal_final", method= {RequestMethod.GET, RequestMethod.POST})
	public ModelAndView payment_paypal_final ( ModelAndView mv, HttpSession session,HttpServletRequest request, HttpServletResponse response
			, BillingVo form) {
		String language = (String) session.getAttribute("language");
		String path = "homePage"+ language;
		mv.setViewName(path+"/payment_paypal_final");
		return mv;
	}
	@RequestMapping(value="payment_paypal_cancel", method= {RequestMethod.GET, RequestMethod.POST})
	public ModelAndView payment_paypal_cancel ( ModelAndView mv, HttpSession session,HttpServletRequest request, HttpServletResponse response
			, BillingVo form) {
		String language = (String) session.getAttribute("language");
		String path = "homePage"+ language;
		mv.setViewName(path+"/payment_paypal_cancel");
		return mv;
	}
	@RequestMapping(value="fail", method= {RequestMethod.GET, RequestMethod.POST})
	public void tossfail ( ModelAndView mv, HttpSession session,HttpServletRequest request, HttpServletResponse response
			) {
		 response.setContentType("text/html; charset=UTF-8");
		 String comment= "????????? ??????????????????. ?????? ??????????????????, ?????? ????????? ??????????????? ????????????.";		
		 PrintWriter out;
		try {
			out = response.getWriter();
			out.println("<script>alert('"+comment+"'); location.href='./pricing';</script>");
			out.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@RequestMapping(value="order",method=RequestMethod.GET)
	public String order(Model mv,HttpSession session,HttpServletRequest request, HttpServletResponse response
			, BillingVo form) {
		String language = (String) session.getAttribute("language");
		String path = "homePage"+ language;
		BillingVo billingVo = (BillingVo) session.getAttribute("billing");
		MemberVo memberVo = (MemberVo) session.getAttribute("memberVo");
		
		if (memberVo == null) {
			return "redirect:/";
		}
		
		String payUserId = memberVo.getUserId();
		
		logger.info("billingVo {}", billingVo);
		billingVo.setPAY_PLATFORM("bank");
		billingVo.setPAY_TYPE("bank");
		
		sendEmail.sendBankTransfer(payUserId, billingVo);
		mv.addAttribute("billing",billingVo);
		
		return path+"/order";
	}
	
	@RequestMapping(value="goSubscribeComplete",method=RequestMethod.GET)
	public String goSubscribeComplete(Model mv,HttpSession session,HttpServletRequest request, HttpServletResponse response
			, BillingVo form) {
		String language = (String) session.getAttribute("language");
		String path = "homePage"+ language;
		MemberVo memberVo = (MemberVo) session.getAttribute("memberVo");
		BillingVo billingVo = (BillingVo) session.getAttribute("billing");
		logger.info("billingVo- {}", billingVo);
		if (billingVo == null) {
			return path + "/pricing";
		}
		
		if (billingVo.getPaymentKey().equals("")) {
			return path + "/pricing";
		}
		
		logger.info("form {}", form);
		memberVo = memberService.selectPayCompleteUser(memberVo);
		session.setAttribute("memberVo",memberVo);
		session.removeAttribute("billing");
		session.removeAttribute("billingOld");
		mv.addAttribute("billing", billingVo);
		return path+"/subscribe_complete";
	}
	
	@RequestMapping(value="goSubscribeCompleteTrial", method=RequestMethod.GET) 
	public String goSubscribeCompleteTrial (Model mv,HttpSession session,HttpServletRequest request, HttpServletResponse response
			, BillingVo form) {
		String language = (String) session.getAttribute("language");
		String path = "homePage"+ language;
		MemberVo memberVo = (MemberVo) session.getAttribute("memberVo");
		BillingVo billingVo = (BillingVo) session.getAttribute("billing");
		logger.info("billingVo- {}", billingVo);
		if (billingVo == null) {
			return path + "/pricing";
		}
		memberVo = memberService.selectPayCompleteUser(memberVo);
		session.setAttribute("memberVo",memberVo);
		session.removeAttribute("billing");
		session.removeAttribute("billingOld");
		mv.addAttribute("billing", billingVo);
		return path+"/subscribe_complete";
	}
	
	@RequestMapping(value="invoice",method=RequestMethod.GET)
	public String goInvoice(Model mv,HttpSession session,HttpServletRequest request, HttpServletResponse response) {
		String language = (String) session.getAttribute("language");
		String billingNo= request.getParameter("no");
		Map<String,Object> param = new HashMap<String,Object>();
		param.put("billingNo", billingNo);
		Map<String ,Object> result = billingService.selectSubscriptOne(param);
		logger.info("result {}", result);
		
		result.put("PAY_TAX",(int) result.get("PAY_PRICE") - (int) result.get("PAY_PRICE") * 100/110);
		String payPlatform = (String) result.get("PAY_PLATFORM");
		if (payPlatform.indexOf("paypal") > -1) {
			language = "_EN";
		} else {
			language = "";
		}

		mv.addAttribute("result",result);
		mv.addAttribute("language",language);
		
		String path = "homePage"+ language;
		logger.info("path {}", path);
		
		return path + "/invoice";
	}
	
}
