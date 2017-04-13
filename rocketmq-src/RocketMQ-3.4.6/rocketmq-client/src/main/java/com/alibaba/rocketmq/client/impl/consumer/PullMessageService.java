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
package com.alibaba.rocketmq.client.impl.consumer;

import com.alibaba.rocketmq.client.impl.factory.MQClientInstance;
import com.alibaba.rocketmq.client.log.ClientLogger;
import com.alibaba.rocketmq.common.ServiceThread;
import org.slf4j.Logger;

import java.util.concurrent.*;


/**
 * @author shijia.wxr   从rocket nameserver或者broke获取消息
 */
public class PullMessageService extends ServiceThread {
    private final Logger log = ClientLogger.getLog();
    /*  拉取消息的请求全部放入该队列中,在
    updateProcessQueueTableInRebalance-> RebalancePushImpl.dispatchPullRequest->PullMessageService.executePullRequestImmediately 中更新,
    PullMessageService.run 中通过该 pullRequestQueue 中的PullRequest来拉取消息 */
    ////MQClientInstance.start(this.rebalanceService.start();)中进行队列rebalance处理的时候会更新该 queue
    private final LinkedBlockingQueue<PullRequest> pullRequestQueue = new LinkedBlockingQueue<PullRequest>();
    private final MQClientInstance mQClientFactory;
    private final ScheduledExecutorService scheduledExecutorService = Executors
        .newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "PullMessageServiceScheduledThread");
            }
        });;


    public PullMessageService(MQClientInstance mQClientFactory) {
        this.mQClientFactory = mQClientFactory;
    }

    public void executePullRequestLater(final PullRequest pullRequest, final long timeDelay) {
        this.scheduledExecutorService.schedule(new Runnable() {

            @Override
            public void run() {
                PullMessageService.this.executePullRequestImmediately(pullRequest);
            }
        }, timeDelay, TimeUnit.MILLISECONDS);
    }


    public void executeTaskLater(final Runnable r, final long timeDelay) {
        this.scheduledExecutorService.schedule(r, timeDelay, TimeUnit.MILLISECONDS);
    }

    // updateProcessQueueTableInRebalance-> RebalancePushImpl.dispatchPullRequest->PullMessageService.executePullRequestImmediately
    public void executePullRequestImmediately(final PullRequest pullRequest) {
        try {
            this.pullRequestQueue.put(pullRequest);
        }
        catch (InterruptedException e) {
            log.error("executePullRequestImmediately pullRequestQueue.put", e);
        }
    }


    /**
     * 获取pullrequest的消费者分组对应的MQConsumerInner进行消费。
     * @param pullRequest
     */
    private void pullMessage(final PullRequest pullRequest) { /* run中执行，就是根据拉取消息的pullRequest请求来从从服务端获取消息 */
        final MQConsumerInner consumer = this.mQClientFactory.selectConsumer(pullRequest.getConsumerGroup());
        if (consumer != null) {
            DefaultMQPushConsumerImpl impl = (DefaultMQPushConsumerImpl) consumer;
            impl.pullMessage(pullRequest);
        }
        else {
            log.warn("No matched consumer for the PullRequest {}, drop it", pullRequest);
        }
    }

    @Override  //ProxyServer.doSubscribe->consumer.start->mQClientFactory.start->this.pullMessageService.start()
    public void run() { /* 執行start就會運行run */
        log.info(this.getServiceName() + " service started");

        while (!this.isStoped()) {
            try {
                PullRequest pullRequest = this.pullRequestQueue.take(); //拉取队列pullRequestQueue中指定的消息请求
                if (pullRequest != null) {
                    this.pullMessage(pullRequest);
                }
            }
            catch (InterruptedException e) {
            }
            catch (Exception e) {
                log.error("Pull Message Service Run Method exception", e);
            }
        }

        log.info(this.getServiceName() + " service end");
    }


    @Override
    public String getServiceName() {
        return PullMessageService.class.getSimpleName();
    }


    public ScheduledExecutorService getScheduledExecutorService() {
        return scheduledExecutorService;
    }
}
