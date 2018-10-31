def timer(delay) = Coeffect({. kind = "timer", delay = delay .})

"first result" | (timer(2000) >> "second result")