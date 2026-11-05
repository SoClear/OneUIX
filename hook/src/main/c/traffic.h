#pragma once

#include <stdbool.h>

typedef struct {
    double tx;
    double rx;
} TrafficSpeed;

void traffic_reset(void);
bool traffic_sample(TrafficSpeed *speed);
