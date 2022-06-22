package com.netMiner.app.interceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

public class RootHandlerInterseceptor implements HandlerInterceptor {
	private static final Logger logger= LoggerFactory.getLogger(SessionAdmLoginInterceptor.class);

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		
		logger.info("getServerName() {}",request.getServerName());
		
		if (request.isSecure()
				|| "weespk.iptime.org".equals(request.getServerName())
				|| "dev.netminer365.com".equals(request.getServerName())) {
		} else {
			String url = "https://" + request.getServerName();
			response.sendRedirect(url);
			return false;
		}
		
		return true;
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) throws Exception {
		
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
			throws Exception {
		
	}

}
