/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.alibaba.rocketmq.common.filter;

import com.alibaba.rocketmq.common.protocol.heartbeat.SubscriptionData;

import java.net.URL;


/**
 * @author shijia.wxr
 */
public class FilterAPI {
    public static String simpleClassName(final String className) {
        String simple = className;
        int index = className.lastIndexOf(".");
        if (index >= 0) {
            simple = className.substring(index + 1);
        }

        return simple;
    }


    public static URL classFile(final String className) {
        final String javaSource = simpleClassName(className) + ".java";
        URL url = FilterAPI.class.getClassLoader().getResource(javaSource);
        return url;
    }


    public static SubscriptionData buildSubscriptionData(final String consumerGroup, String topic,
            String subString) throws Exception {
        SubscriptionData subscriptionData = new SubscriptionData();
        subscriptionData.setTopic(topic);
        subscriptionData.setSubString(subString);

        /*为null ,* ，或者空字符串 都认为是订阅topic的所有消息。*/
        if (null == subString || subString.equals(SubscriptionData.SUB_ALL) || subString.length() == 0) {
            subscriptionData.setSubString(SubscriptionData.SUB_ALL);
        }
        else {
            //按双竖线切分tag表达式，得到多个tag , trim运算以后，得到每一个tag以及每一个tag的hashcode (因为在broker是用msgTag的hashcode
            // 来做过滤的。)
            String[] tags = subString.split("\\|\\|");
            if (tags != null && tags.length > 0) {
                for (String tag : tags) {
                    if (tag.length() > 0) {
                        String trimString = tag.trim();
                        if (trimString.length() > 0) {
                            subscriptionData.getTagsSet().add(trimString);
                            subscriptionData.getCodeSet().add(trimString.hashCode());
                        }
                    }
                }
            }
            else {
                throw new Exception("subString split error");
            }
        }

        return subscriptionData;
    }
}
