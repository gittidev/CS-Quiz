import { Provider } from "../types/auth";

/**
 * ✅ OAuth2 로그인 로직을 관리하는 커스텀 훅 (useOAuthLogin)
 * - 소셜 로그인 버튼 클릭 시, 백엔드에 로그인 요청을 보내는 함수
 * - 로그인 중인지 여부를 상태로 관리
 * - 서비스 로직에 집중하고, UI 로직을 분리하여 관리
 * @returns {loggingIn, loginWithProvider}
 */
// src/hooks/useOAuthLogin.ts

export const useOAuthLogin = () => {
  const loginWithProvider = (provider: Provider) => {
    const apiUrl = process.env.NEXT_PUBLIC_API_URL;
    window.location.href = `${apiUrl}/api/oauth2/authorize/${provider}`;
  };

  return { loginWithProvider };
};
