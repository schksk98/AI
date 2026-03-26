package com.zhaopx.ai.controller;

import com.zhaopx.ai.service.AnswerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Flux;
import org.springframework.http.MediaType;

/**
 * @Description
 * @Author: ZhaoPengXiang
 * @Date: 2026-03-26 14:32
 */
@Slf4j
@RequestMapping("/answer")
@Controller
public class AnswerController {

    @Autowired
    private AnswerService answerService;

    @GetMapping(value = "/myQuestion")
    public Flux<String> myQuestion(@RequestParam("question") String question) {
        return answerService.myQuestion(question);
    }
}
