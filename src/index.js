import { Container, getContainer } from "@cloudflare/containers";

export class SpringContainer extends Container {
  defaultPort = 8080;
  sleepAfter = "10m";
}

export default {
  async fetch(request, env) {
    const container = getContainer(env.SPRING_CONTAINER, "default-instance");
    return container.fetch(request);
  },
};
